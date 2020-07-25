package com.spqrta.camera2demo.base

import android.app.Activity
import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.disposables.Disposable

abstract class BaseFragment<T : Activity>: Fragment() {

    fun mainActivity(): T = activity as T

//    var toolbar: CustomToolbar? = null
    var compositeDisposable: CompositeDisposable = CompositeDisposable()

    open val delegates by lazy {
        listOf<FragmentDelegate<Activity>>(
//            HideKeyboardDelegate(this)
        )
    }

    override fun onResume() {
        super.onResume()
        delegates.forEach { it.onResume() }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
//        toolbar = view.findViewById(R.id.customToolbar)
        delegates.forEach { it.onViewCreated() }
//        debug = this
    }

    override fun onDestroyView() {
        super.onDestroyView()
        delegates.forEach { it.onDestroyView() }
        disposeAll()
    }

    override fun onPause() {
        super.onPause()
        delegates.forEach { it.onPause() }
    }

    override fun onStop() {
        super.onStop()
        delegates.forEach { it.onStop() }
    }

    override fun onDestroy() {
        super.onDestroy()
        delegates.forEach { it.onDestroy() }
        disposeAll()
//        Timber.v("--unsubscribe ${System.identityHashCode(this)}")
//        Timber.v("subs |${System.identityHashCode(BaseFragment.debug!!)}| ${BaseFragment.debug?.compositeDisposable?.size()}")
    }

    fun onLeave() {
        delegates.forEach { it.onLeave() }
        disposeAll()
//        Timber.v("--unsubscribe ${System.identityHashCode(this)}")
    }

    fun disposeAll() {
        compositeDisposable.dispose()
        compositeDisposable = CompositeDisposable()
    }

    abstract class FragmentDelegate<M: Activity>(protected val fragment: BaseFragment<M>) {
        open fun onViewCreated() {}
        open fun onResume() {}
        open fun onDestroyView() {}
        open fun onPause() {}
        open fun onStop() {}
        open fun onDestroy() {}
        open fun onLeave() {}
    }

    fun <T> Single<T>.subscribeManaged(onSuccess: (T) -> Unit): Disposable {
        val disposable = subscribe(onSuccess)
        compositeDisposable.add(disposable)
        return disposable
    }

    fun <T> Single<T>.subscribeManaged(
        onSuccess: (T) -> Unit,
        onError: (Throwable) -> Unit
    ): Disposable {
        val disposable = subscribe(onSuccess, onError)
        compositeDisposable.add(disposable)
        return disposable
    }

    fun <T> Observable<T>.subscribeManaged(onSuccess: (T) -> Unit): Disposable {
        val disposable = subscribe(onSuccess)
        compositeDisposable.add(disposable)
        return disposable
    }

    fun <T> Observable<T>.subscribeManaged(
        onSuccess: (T) -> Unit,
        onError: (Throwable) -> Unit
    ): Disposable {
        val disposable = subscribe(onSuccess, onError)
        compositeDisposable.add(disposable)
        return disposable
    }

    open fun onBackPressed(): Boolean {
        return false
    }

}