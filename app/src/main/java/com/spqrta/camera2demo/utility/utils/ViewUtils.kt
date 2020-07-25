package com.spqrta.camera2demo.utility.utils

import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.AdapterView
import io.reactivex.subjects.BehaviorSubject

abstract class SimpleTextWatcher : TextWatcher {
    open fun afterChanged(text: String) {}

    override fun afterTextChanged(s: Editable?) {
        afterChanged(s.toString())
    }

    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
}

class RxTextWatcher : TextWatcher {
    val subject = BehaviorSubject.create<String>()

    override fun afterTextChanged(s: Editable?) {
        subject.onNext(s.toString())
    }

    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
    }

    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
    }
}



abstract class SimpleOnSelectedItemListener : AdapterView.OnItemSelectedListener {

    override fun onNothingSelected(parent: AdapterView<*>?) {
    }

    override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
        onItemSelected(position, parent!!.adapter.getItem(position))
    }

    abstract fun onItemSelected(position: Int, item: Any)
}