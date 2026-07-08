package com.a3solution.compose_practice

import android.os.Bundle
import android.util.Log
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.fragment.app.commit
import com.a3solution.compose_practice.ui.theme.ScannerAppTheme

private const val TAG = "ComposeLifecycle"

// Changing to AppCompatActivity to support Fragments
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "Activity: onCreate")
        enableEdgeToEdge()
        
        // Using setContent to mix Compose and Fragment testing
        setContent {
            ScannerAppTheme {
                var useFragment by remember { mutableStateOf(false) }

                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    if (useFragment) {
                        // This uses the FragmentContainerView defined in code/layout
                        FragmentContainerScreen(onBack = { useFragment = false })
                    } else {
                        LifecycleDemo(onShowFragment = { useFragment = true })
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        Log.d(TAG, "Activity: onStart")
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "Activity: onResume")
    }

    override fun onPause() {
        super.onPause()
        Log.d(TAG, "Activity: onPause")
    }

    override fun onStop() {
        super.onStop()
        Log.d(TAG, "Activity: onStop")
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Activity: onDestroy")
    }

    @Composable
    fun FragmentContainerScreen(onBack: () -> Unit) {
        Column(modifier = Modifier.fillMaxSize()) {
            Button(onClick = onBack, modifier = Modifier.padding(16.dp)) {
                Text("Back to Compose Only")
            }
            
            // AndroidView could be used, but for a simple "Fragment inside Activity" demo
            // we'll just use a FrameLayout and FragmentManager
            androidx.compose.ui.viewinterop.AndroidView(
                factory = { context ->
                    android.widget.FrameLayout(context).apply {
                        id = android.view.View.generateViewId()
                        val fragment = LifecycleFragment()
                        supportFragmentManager.commit {
                            replace(id, fragment, "LIFECYCLE_FRAGMENT")
                        }
                    }
                },
                onRelease = {
                    val fragment = supportFragmentManager.findFragmentByTag("LIFECYCLE_FRAGMENT")
                    if (fragment != null) {
                        supportFragmentManager.commit {
                            remove(fragment)
                        }
                    }
                },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
fun LifecycleDemo(onShowFragment: () -> Unit) {
    var showChild by remember { mutableStateOf(true) }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            painter = painterResource(id = R.drawable.ic_compose_logo),
            contentDescription = "Compose Logo",
            modifier = Modifier.size(100.dp)
        )
        Spacer(modifier = Modifier.height(20.dp))
        Text("Compose Lifecycle Practice", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(20.dp))
        
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { showChild = !showChild }) {
                Text(if (showChild) "Destroy Child" else "Create Child")
            }
            Button(onClick = onShowFragment) {
                Text("Open Fragment")
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        if (showChild) {
            LifecycleChild()
        }
    }
}

@Composable
fun LifecycleChild() {
    var count by remember { mutableIntStateOf(0) }

    Log.d(TAG, "1. [RECOMPOSITION] Child body executing. Current count: $count")

    LaunchedEffect(Unit) {
        Log.d(TAG, "2. [LAUNCHED_EFFECT] Child ENTERED composition")
    }

    SideEffect {
        Log.d(TAG, "3. [SIDE_EFFECT] Composition successful for count: $count")
    }

    DisposableEffect(Unit) {
        Log.d(TAG, "4. [DISPOSABLE_EFFECT] Initializing setup...")
        onDispose {
            Log.d(TAG, "5. [ON_DISPOSE] Child LEAVING composition")
        }
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Count: $count", style = MaterialTheme.typography.displayMedium)
        Button(onClick = { count++ }) {
            Text("Trigger Recomposition")
        }
    }
}
