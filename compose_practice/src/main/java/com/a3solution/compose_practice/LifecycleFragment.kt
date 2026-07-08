package com.a3solution.compose_practice

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.fragment.app.Fragment
import com.a3solution.compose_practice.ui.theme.ScannerAppTheme

private const val TAG = "FragmentLifecycle"

class LifecycleFragment : Fragment() {

    override fun onAttach(context: Context) {
        super.onAttach(context)
        Log.d(TAG, "Fragment: 1. onAttach")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "Fragment: 2. onCreate")
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        Log.d(TAG, "Fragment: 3. onCreateView (Creating ComposeView)")
        return ComposeView(requireContext()).apply {
            setContent {
                ScannerAppTheme {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                "Inside Fragment",
                                style = MaterialTheme.typography.headlineSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "Compose is now tied to Fragment's View Lifecycle",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            
                            // You can still use Compose-specific lifecycle tools here
                            LifecycleChild() 
                        }
                    }
                }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.d(TAG, "Fragment: 4. onViewCreated")
    }

    override fun onViewStateRestored(savedInstanceState: Bundle?) {
        super.onViewStateRestored(savedInstanceState)
        Log.d(TAG, "Fragment: 4.5 onViewStateRestored")
    }

    override fun onStart() {
        super.onStart()
        Log.d(TAG, "Fragment: 5. onStart")
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "Fragment: 6. onResume")
    }

    override fun onPause() {
        super.onPause()
        Log.d(TAG, "Fragment: 7. onPause")
    }

    override fun onStop() {
        super.onStop()
        Log.d(TAG, "Fragment: 8. onStop")
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        Log.d(TAG, "Fragment: 8.5 onSaveInstanceState")
    }

    override fun onDestroyView() {
        super.onDestroyView()
        Log.d(TAG, "Fragment: 9. onDestroyView (ComposeView disposed here)")
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Fragment: 10. onDestroy")
    }

    override fun onDetach() {
        super.onDetach()
        Log.d(TAG, "Fragment: 11. onDetach")
    }
}
