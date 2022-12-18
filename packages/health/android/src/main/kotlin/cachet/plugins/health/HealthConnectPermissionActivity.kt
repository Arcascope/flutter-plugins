package cachet.plugins.health

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultCallback
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch

class HealthConnectPermissionActivity : ComponentActivity(),
    ActivityResultCallback<Set<HealthPermission>> {

    private val mainScope = MainScope()

    private val requestPermissionActivityContract =
        PermissionController.createRequestPermissionResultContract()

    private val requestPermissions = registerForActivityResult(
        requestPermissionActivityContract, this
    )

    var setOfPermissions = emptySet<HealthPermission>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.health)

        setOfPermissions = getPermissions()
        val healthConnectClient = HealthConnectClient.getOrCreate(this)

        mainScope.launch {
            checkPermissionsAndRun(healthConnectClient, setOfPermissions)
        }
    }

    override fun onActivityResult(result: Set<HealthPermission>) {
        if (result.containsAll(setOfPermissions)) {
            permissionGranted()
        } else {
            permissionDenied()
        }
    }

    private suspend fun checkPermissionsAndRun(
        healthConnectClient: HealthConnectClient, permission: Set<HealthPermission>
    ) {
        val granted = healthConnectClient.permissionController.getGrantedPermissions(permission)
        if (granted.containsAll(permission)) {
            permissionGranted()
        } else {
            requestPermissions.launch(permission)
        }
    }

    private fun getPermissions(): Set<HealthPermission> {
        intent.extras?.run {
            val permissions = this.getIntegerArrayList(PERMISSIONS)!!.toList()
            val types = this.getStringArrayList(TYPES)!!.toList()

            return callToHealthConnectTypes(types, permissions).toSet()
        }

        return emptySet()
    }

    private fun permissionGranted() {
        setResult(Activity.RESULT_OK)
        finish()
    }

    private fun permissionDenied() {
        setResult(Activity.RESULT_CANCELED)
        finish()
    }

    companion object {
        private const val TYPES = "TYPES"
        private const val PERMISSIONS = "PERMISSIONS"
        const val RESULT = 10

        fun open(context: Context, types: ArrayList<String>, permissions: ArrayList<Int>) =
            Intent(context, HealthConnectPermissionActivity::class.java).apply {
                putExtra(PERMISSIONS, permissions)
                putExtra(TYPES, types)
            }

        suspend fun hasAllRequiredPermissions(
            context: Context, arguments: Pair<List<String>, List<Int>>
        ): Boolean {
            val healthConnectClient = HealthConnectClient.getOrCreate(context)
            val permissions = callToHealthConnectTypes(arguments.first, arguments.second).toSet()
            val granted =
                healthConnectClient.permissionController.getGrantedPermissions(permissions)
            return granted.containsAll(permissions)
        }
    }
}