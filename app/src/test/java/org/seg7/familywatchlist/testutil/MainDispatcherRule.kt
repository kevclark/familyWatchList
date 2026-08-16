package org.seg7.familywatchlist.testutil

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/**
 * ViewModels under test (M2a) use `viewModelScope.launch`, which dispatches on
 * `Dispatchers.Main`. JVM unit tests have no real main thread, so this rule installs a test
 * dispatcher for the duration of each test. Unconfined so `launch { ... }` bodies run eagerly,
 * which keeps the ViewModel tests below free of manual `advanceUntilIdle()` calls.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(
    private val dispatcher: TestDispatcher = UnconfinedTestDispatcher(),
) : TestWatcher() {
    override fun starting(description: Description) {
        kotlinx.coroutines.Dispatchers.setMain(dispatcher)
    }

    override fun finished(description: Description) {
        kotlinx.coroutines.Dispatchers.resetMain()
    }
}
