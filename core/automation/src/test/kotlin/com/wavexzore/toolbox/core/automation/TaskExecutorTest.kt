package com.wavexzore.toolbox.core.automation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

class TaskExecutorTest {

    private fun task(vararg steps: Step, pkg: String = "com.x", id: String = "t") =
        TaskDef(id = id, pkg = pkg, steps = steps.toList())

    private fun statusOf(progress: List<TaskProgress>): TaskProgress.Status? =
        (progress.lastOrNull() as? TaskProgress.Finished)?.status

    @Test
    fun `full success run with vars`() = runTest {
        val env = FakeTaskEnv().apply { tree = TestTree.root(TestTree.node(text = "跳过")) }
        val progress = TaskExecutor(env).run(
            task(
                Step.WaitFor("[text=跳过]", timeoutMs = 1_000),
                Step.Click("[text=跳过]", timeoutMs = 1_000),
                Step.ReadText("[text=跳过]", into = "v"),
                Step.AssertText("\${v}", timeoutMs = 1_000),
                Step.Delay(50),
            ),
        ).toList()

        assertEquals(TaskProgress.Status.SUCCESS, statusOf(progress))
        assertTrue(env.actions.contains("click:跳过"))
        assertTrue(env.logs.any { it.contains("success") })
        assertEquals(5, progress.count { it is TaskProgress.StepStarted })
        assertTrue(progress.filterIsInstance<TaskProgress.StepFinished>().all { it.ok })
    }

    @Test
    fun `waitfor is woken by event`() = runTest {
        val env = FakeTaskEnv()
        val executor = TaskExecutor(env)
        val progress = mutableListOf<TaskProgress>()
        val job = launch { executor.run(task(Step.WaitFor("[text=甲]", timeoutMs = 10_000))).collect { progress += it } }
        runCurrent()
        advanceTimeBy(100)
        env.tree = TestTree.root(TestTree.node(text = "甲"))
        env.wake()
        advanceUntilIdle()
        assertEquals(TaskProgress.Status.SUCCESS, statusOf(progress))
        assertTrue(currentTime < 2_000)
        job.join()
    }

    @Test
    fun `waitfor timeout aborts`() = runTest {
        val env = FakeTaskEnv()
        val progress = TaskExecutor(env).run(
            task(Step.WaitFor("[text=甲]", timeoutMs = 1_000), Step.Click("[text=乙]", timeoutMs = 1_000)),
        ).toList()
        assertEquals(TaskProgress.Status.FAILED, statusOf(progress))
        assertTrue((progress.last() as TaskProgress.Finished).reason!!.contains("timeout"))
        assertEquals(1, progress.count { it is TaskProgress.StepStarted })
    }

    @Test
    fun `timeout with skip policy continues`() = runTest {
        val env = FakeTaskEnv()
        val progress = TaskExecutor(env).run(
            task(
                Step.WaitFor("[text=甲]", timeoutMs = 1_000, failPolicy = FailPolicy.SKIP),
                Step.Delay(10),
            ),
        ).toList()
        assertEquals(TaskProgress.Status.SUCCESS, statusOf(progress))
        val skipped = progress.filterIsInstance<TaskProgress.StepFinished>().first()
        assertTrue(skipped.skipped)
        assertTrue(!skipped.ok)
        assertEquals(2, progress.count { it is TaskProgress.StepStarted })
    }

    @Test
    fun `click retries then succeeds`() = runTest {
        val env = FakeTaskEnv().apply {
            tree = TestTree.root(TestTree.node(text = "确定"))
            clickScript.addAll(listOf(false, false, true))
        }
        val progress = TaskExecutor(env).run(
            task(Step.Click("[text=确定]", retry = 2, timeoutMs = 5_000)),
        ).toList()
        assertEquals(TaskProgress.Status.SUCCESS, statusOf(progress))
        assertEquals(3, env.actions.count { it.startsWith("click:") })
    }

    @Test
    fun `click optional absent is skipped`() = runTest {
        val env = FakeTaskEnv().apply { tree = TestTree.root() }
        val progress = TaskExecutor(env).run(
            task(Step.Click("[text=没有]", optional = true, timeoutMs = 1_000), Step.Delay(10)),
        ).toList()
        assertEquals(TaskProgress.Status.SUCCESS, statusOf(progress))
        assertTrue(progress.filterIsInstance<TaskProgress.StepFinished>().first().skipped)
        assertTrue(env.actions.none { it.startsWith("click:") })
    }

    @Test
    fun `click absent with abort fails after wait timeout`() = runTest {
        val env = FakeTaskEnv().apply { tree = TestTree.root() }
        val progress = TaskExecutor(env).run(task(Step.Click("[text=没有]", timeoutMs = 800))).toList()
        assertEquals(TaskProgress.Status.FAILED, statusOf(progress))
        assertTrue((progress.last() as TaskProgress.Finished).reason!!.contains("timeout"))
    }

    @Test
    fun `waitactivity success and timeout`() = runTest {
        val env = FakeTaskEnv().apply { activity = "com.x.DetailActivity" }
        val ok = TaskExecutor(env).run(task(Step.WaitActivity("DetailActivity", timeoutMs = 1_000))).toList()
        assertEquals(TaskProgress.Status.SUCCESS, statusOf(ok))

        val env2 = FakeTaskEnv().apply { activity = "com.x.MainActivity" }
        val fail = TaskExecutor(env2).run(task(Step.WaitActivity("DetailActivity", timeoutMs = 1_000))).toList()
        assertEquals(TaskProgress.Status.FAILED, statusOf(fail))
    }

    @Test
    fun `cancel interrupts wait quickly`() = runTest {
        val env = FakeTaskEnv()
        val executor = TaskExecutor(env)
        val progress = mutableListOf<TaskProgress>()
        val job = launch { executor.run(task(Step.WaitFor("[text=甲]", timeoutMs = 60_000))).collect { progress += it } }
        runCurrent()
        advanceTimeBy(200)
        executor.cancel()
        advanceUntilIdle()
        assertEquals(TaskProgress.Status.CANCELLED, statusOf(progress))
        assertTrue(currentTime < 5_000)
        job.join()
    }

    @Test
    fun `cancel before next step stops run`() = runTest {
        val env = FakeTaskEnv()
        val executor = TaskExecutor(env)
        val progress = mutableListOf<TaskProgress>()
        val job = launch {
            executor.run(task(Step.Delay(1_000), Step.Delay(1_000), Step.Delay(1_000))).collect { progress += it }
        }
        runCurrent()
        advanceTimeBy(1_100)
        executor.cancel()
        advanceUntilIdle()
        assertEquals(TaskProgress.Status.CANCELLED, statusOf(progress))
        assertTrue(progress.count { it is TaskProgress.StepStarted } <= 2)
        job.join()
    }

    @Test
    fun `scroll until times`() = runTest {
        val env = FakeTaskEnv()
        val progress = TaskExecutor(env).run(
            task(Step.Scroll(ScrollDir.DOWN, Until.Times(3), timeoutMs = 8_000)),
        ).toList()
        assertEquals(TaskProgress.Status.SUCCESS, statusOf(progress))
        assertEquals(3, env.actions.count { it == "scrollDown" })
    }

    @Test
    fun `scroll until selector stops at hit`() = runTest {
        val env = FakeTaskEnv().apply {
            scrollHook = { _ -> tree = TestTree.root(TestTree.node(text = "目标")) }
        }
        val progress = TaskExecutor(env).run(
            task(Step.Scroll(ScrollDir.DOWN, Until.Selector("[text=目标]", maxScrolls = 5), timeoutMs = 8_000)),
        ).toList()
        assertEquals(TaskProgress.Status.SUCCESS, statusOf(progress))
        assertEquals(1, env.actions.count { it == "scrollDown" })
    }

    @Test
    fun `scroll until selector fails at max`() = runTest {
        val env = FakeTaskEnv().apply { tree = TestTree.root() }
        val progress = TaskExecutor(env).run(
            task(Step.Scroll(ScrollDir.DOWN, Until.Selector("[text=无]", maxScrolls = 3), timeoutMs = 8_000, failPolicy = FailPolicy.ABORT)),
        ).toList()
        assertEquals(TaskProgress.Status.FAILED, statusOf(progress))
        assertEquals(3, env.actions.count { it == "scrollDown" })
    }

    @Test
    fun `assert text mismatch aborts`() = runTest {
        val env = FakeTaskEnv().apply { tree = TestTree.root(TestTree.node(text = "甲")) }
        val progress = TaskExecutor(env).run(task(Step.AssertText("乙", timeoutMs = 500))).toList()
        assertEquals(TaskProgress.Status.FAILED, statusOf(progress))
        assertTrue((progress.last() as TaskProgress.Finished).reason!!.contains("timeout"))
    }

    @Test
    fun `open app records action`() = runTest {
        val env = FakeTaskEnv()
        val progress = TaskExecutor(env).run(task(Step.OpenApp("com.tencent.mm"))).toList()
        assertEquals(TaskProgress.Status.SUCCESS, statusOf(progress))
        assertTrue(env.actions.contains("open:com.tencent.mm"))
    }

    @Test
    fun `same package runs are serialized`() = runTest {
        val env = FakeTaskEnv()
        val executor = TaskExecutor(env)
        val order = mutableListOf<String>()
        val j1 = launch {
            executor.run(task(Step.Delay(1_000), id = "t1")).collect {
                if (it is TaskProgress.Started) order += "start:t1"
                if (it is TaskProgress.Finished) order += "finish:t1"
            }
        }
        runCurrent()
        val j2 = launch {
            executor.run(task(Step.Delay(10), id = "t2")).collect {
                if (it is TaskProgress.Started) order += "start:t2"
                if (it is TaskProgress.Finished) order += "finish:t2"
            }
        }
        advanceUntilIdle()
        assertEquals(listOf("start:t1", "finish:t1", "start:t2", "finish:t2"), order)
        j1.join()
        j2.join()
    }

    @Test
    fun `different packages run concurrently`() = runTest {
        val env = FakeTaskEnv()
        val executor = TaskExecutor(env)
        val order = mutableListOf<String>()
        val j1 = launch {
            executor.run(task(Step.Delay(1_000), pkg = "com.a", id = "ta")).collect {
                if (it is TaskProgress.Started) order += "start:ta"
                if (it is TaskProgress.Finished) order += "finish:ta"
            }
        }
        runCurrent()
        val j2 = launch {
            executor.run(task(Step.Delay(10), pkg = "com.b", id = "tb")).collect {
                if (it is TaskProgress.Started) order += "start:tb"
                if (it is TaskProgress.Finished) order += "finish:tb"
            }
        }
        advanceUntilIdle()
        assertEquals(listOf("start:ta", "start:tb", "finish:tb", "finish:ta"), order)
        j1.join()
        j2.join()
    }

    @Test
    fun `empty task succeeds immediately`() = runTest {
        val progress = TaskExecutor(FakeTaskEnv()).run(task()).toList()
        assertEquals(TaskProgress.Status.SUCCESS, statusOf(progress))
        assertEquals(2, progress.size)
    }

    @Test
    fun `continue policy keeps task alive`() = runTest {
        val env = FakeTaskEnv().apply { tree = TestTree.root() }
        val progress = TaskExecutor(env).run(
            task(
                Step.AssertText("甲", timeoutMs = 300, failPolicy = FailPolicy.CONTINUE),
                Step.Delay(10),
            ),
        ).toList()
        assertEquals(TaskProgress.Status.SUCCESS, statusOf(progress))
        val failed = progress.filterIsInstance<TaskProgress.StepFinished>().first()
        assertTrue(!failed.ok && failed.skipped)
    }
}
