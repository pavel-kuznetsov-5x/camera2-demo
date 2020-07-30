package com.spqrta.camera2demo.base

import android.os.Bundle
import androidx.navigation.NavController
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.NavigationUI
import com.spqrta.camera2demo.R
import org.jetbrains.annotations.TestOnly

//todo to reusables
open class NavActivity: BaseActivity() {

    private lateinit var navController: NavController

    override val layoutRes = R.layout.activity_nav

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        navController = findNavController(R.id.nav_host)

        navController.addOnDestinationChangedListener { _, destination, bundle ->
            try {
                getCurrentFragment().onLeave()
            } catch (e: IndexOutOfBoundsException) {
            }
        }
    }

    protected fun getCurrentFragment(): BaseFragment<*> {
        val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host)
        return navHostFragment!!.childFragmentManager.fragments[0] as BaseFragment<*>
    }

    override fun onBackPressed() {
        if(!getCurrentFragment().onBackPressed()) {
            super.onBackPressed()
        }
    }

    @TestOnly
    fun getNavigationController(): NavController {
        return navController
    }
}