package com.sslms.console.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Loads data, keeps the previous value while refreshing, exposes reload(). */
class Loader<T>(private val scope: CoroutineScope, private val fetch: suspend () -> T) {
    var data by mutableStateOf<T?>(null)
    var error by mutableStateOf<String?>(null)
    var loading by mutableStateOf(false)
    private var job: Job? = null

    fun reload() {
        job?.cancel()
        job = scope.launch {
            loading = true
            try {
                data = fetch(); error = null
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                error = e.message ?: "Something went wrong"
            } finally {
                loading = false
            }
        }
    }

    fun update(transform: (T) -> T) { data?.let { data = transform(it) } }
}

/** Survives rotation; scoped to the current navigation destination. */
class LoaderStore : ViewModel() {
    val loaders = mutableMapOf<String, Loader<*>>()
}

@Suppress("UNCHECKED_CAST")
@Composable
fun <T> rememberLoader(key: String, refreshMs: Long? = null, fetch: suspend () -> T): Loader<T> {
    val store: LoaderStore = viewModel()
    val loader = store.loaders.getOrPut(key) { Loader(store.viewModelScope, fetch).also { it.reload() } } as Loader<T>
    if (refreshMs != null) {
        LaunchedEffect(key) {
            while (true) { delay(refreshMs); loader.reload() }
        }
    }
    return loader
}

/** App-wide toast-style messages. */
fun interface Notifier { fun show(message: String, error: Boolean) }
val LocalNotify = staticCompositionLocalOf<Notifier> { Notifier { _, _ -> } }
