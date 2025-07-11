// chemin/simule: app/src/main/java/com/xburnsx/toutiebudget/ui/login/composants/GoogleSignInButton.kt
// Dépendances: Jetpack Compose, Material3, Icône Google

package com.xburnsx.toutiebudget.ui.login.composants

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xburnsx.toutiebudget.R

/**
 * Bouton personnalisé pour la connexion Google Sign-In
 * Suit les guidelines de design de Google pour les boutons d'authentification
 */
@Composable
fun GoogleSignInButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    text: String = "Se connecter avec Google",
    enabled: Boolean = true
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(
            width = 1.dp,
            color = if (enabled) Color.LightGray.copy(alpha = 0.5f) else Color.Gray.copy(alpha = 0.3f)
        ),
        color = if (enabled) Color.White else Color.Gray.copy(alpha = 0.6f),
        shadowElevation = if (enabled) 2.dp else 0.dp
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.padding(
                horizontal = 16.dp,
                vertical = 12.dp
            )
        ) {
            // Icône Google
            Icon(
                painter = painterResource(id = R.drawable.ic_google_logo),
                contentDescription = "Logo Google",
                tint = Color.Unspecified, // Important pour conserver les couleurs originales
                modifier = Modifier.size(20.dp)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Text(
                text = text,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = if (enabled) Color.Black.copy(alpha = 0.87f) else Color.Gray
            )
        }
    }
}

/**
 * Variante du bouton Google avec style sombre
 */
@Composable
fun GoogleSignInButtonDark(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    text: String = "Se connecter avec Google",
    enabled: Boolean = true
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = RoundedCornerShape(8.dp),
        color = if (enabled) Color(0xFF4285F4) else Color.Gray.copy(alpha = 0.6f),
        shadowElevation = if (enabled) 4.dp else 0.dp
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.padding(
                horizontal = 16.dp,
                vertical = 12.dp
            )
        ) {
            // Icône Google en blanc pour le thème sombre
            Icon(
                painter = painterResource(id = R.drawable.ic_google_logo),
                contentDescription = "Logo Google",
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Text(
                text = text,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White
            )
        }
    }
}