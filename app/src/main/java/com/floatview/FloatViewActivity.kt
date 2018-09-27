package com.floatview

import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import com.floatview.demo.R

class FloatViewActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_float_view)

        FloatView.getInstance(this).show()
    }
}
