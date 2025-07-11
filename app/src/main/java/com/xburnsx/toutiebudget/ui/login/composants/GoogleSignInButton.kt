// chemin/simule: /ui/login/composants/GoogleSignInButton.kt
package com.xburnsx.toutiebudget.ui.login.composants

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.xburnsx.toutiebudget.R

@Composable
fun GoogleSignInButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    text: String = "Se connecter avec Google"
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(width = 1.dp, color = Color.LightGray.copy(alpha = 0.5f)),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.padding(
                start = 12.dp,
                end = 16.dp,
                top = 12.dp,
                bottom = 12.dp
            )
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_google_logo),
                contentDescription = "Logo Google",
                tint = Color.Unspecified // Important pour ne pas teinter le logo
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = text,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
