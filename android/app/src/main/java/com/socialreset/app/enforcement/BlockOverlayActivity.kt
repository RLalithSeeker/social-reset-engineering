package com.socialreset.app.enforcement

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.socialreset.app.core.model.InterventionLevel

class BlockOverlayActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val appLabel = intent.getStringExtra(EXTRA_APP_LABEL) ?: "this app"
        val level = intent.getStringExtra(EXTRA_LEVEL) ?: InterventionLevel.FRICTION.name
        val rationale = intent.getStringExtra(EXTRA_RATIONALE).orEmpty()

        setContent {
            MaterialTheme {
                BlockOverlayScreen(
                    appLabel = appLabel,
                    level = level,
                    rationale = rationale,
                    onHome = { goHome() },
                    onClose = { finish() },
                )
            }
        }
    }

    private fun goHome() {
        startActivity(
            Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        finish()
    }

    companion object {
        private const val EXTRA_APP_LABEL = "appLabel"
        private const val EXTRA_LEVEL = "level"
        private const val EXTRA_RATIONALE = "rationale"

        fun intent(context: Context, decision: EnforcementController.Decision.ShowIntervention): Intent =
            Intent(context, BlockOverlayActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(EXTRA_APP_LABEL, decision.appLabel)
                .putExtra(EXTRA_LEVEL, decision.level.name)
                .putExtra(EXTRA_RATIONALE, decision.rationale)
    }
}

@Composable
private fun BlockOverlayScreen(
    appLabel: String,
    level: String,
    rationale: String,
    onHome: () -> Unit,
    onClose: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF7F7F2)),
        color = Color(0xFFF7F7F2),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Icon(Icons.Filled.Block, contentDescription = null, tint = Color(0xFFB3261E))
                    Text(
                        "$appLabel is blocked",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        "Social Reset required",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        rationale.ifBlank { level },
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(4.dp))
                    Button(modifier = Modifier.fillMaxWidth(), onClick = onHome) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Home, contentDescription = null)
                            Text("Go home")
                        }
                    }
                    OutlinedButton(modifier = Modifier.fillMaxWidth(), onClick = onClose) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Lock, contentDescription = null)
                            Text("Stay blocked")
                        }
                    }
                }
            }
        }
    }
}
