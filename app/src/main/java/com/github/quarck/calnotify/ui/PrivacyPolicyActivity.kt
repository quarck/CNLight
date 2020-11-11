package com.github.quarck.calnotify.ui

import android.os.Bundle
import com.google.android.material.snackbar.Snackbar
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import com.github.quarck.calnotify.R
import com.github.quarck.calnotify.utils.find

import kotlinx.android.synthetic.main.activity_privacy_policy.*

class PrivacyPolicyActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_privacy_policy)

        setSupportActionBar(find<Toolbar?>(R.id.toolbar))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)

        window.navigationBarColor = ContextCompat.getColor(this, android.R.color.black)
    }

}
