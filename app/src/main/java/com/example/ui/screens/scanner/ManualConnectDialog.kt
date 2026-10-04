package com.example.ui.screens.scanner

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ConnectionConfig
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.CyanPrimary
import com.example.ui.theme.NavyBorder
import com.example.ui.theme.NavyDark
import com.example.ui.theme.NavySurface
import com.example.ui.theme.NavySurfaceVariant

@Composable
fun ManualConnectDialog(
    initialIp: String = "",
    initialPort: Int = 8443,
    initialCode: String = "",
    onDismiss: () -> Unit,
    onConnect: (ConnectionConfig) -> Unit
) {
    var ip by remember { mutableStateOf(initialIp.ifEmpty { "192.168." }) }
    var port by remember { mutableStateOf(initialPort.toString()) }
    var code by remember { mutableStateOf(initialCode) }
    var useSsl by remember { mutableStateOf(true) }
    var errorText by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = NavySurface,
        title = {
            Text(
                text = "Manual PC Connection",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Enter the IP and Port displayed in your PC Screen Recorder app.",
                    fontSize = 13.sp,
                    color = Color(0xFF94A3B8)
                )

                // IP Address
                OutlinedTextField(
                    value = ip,
                    onValueChange = {
                        ip = it
                        errorText = null
                    },
                    label = { Text("PC IP Address") },
                    placeholder = { Text("192.168.1.100") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CyanAccent,
                        unfocusedBorderColor = NavyBorder,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedContainerColor = NavySurfaceVariant,
                        unfocusedContainerColor = NavySurfaceVariant
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("manual_ip_input")
                )

                // Port & Code Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = port,
                        onValueChange = { port = it },
                        label = { Text("Port") },
                        placeholder = { Text("8443") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CyanAccent,
                            unfocusedBorderColor = NavyBorder,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedContainerColor = NavySurfaceVariant,
                            unfocusedContainerColor = NavySurfaceVariant
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("manual_port_input")
                    )

                    OutlinedTextField(
                        value = code,
                        onValueChange = { code = it },
                        label = { Text("Pairing Code") },
                        placeholder = { Text("Optional") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CyanAccent,
                            unfocusedBorderColor = NavyBorder,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedContainerColor = NavySurfaceVariant,
                            unfocusedContainerColor = NavySurfaceVariant
                        ),
                        modifier = Modifier
                            .weight(1.2f)
                            .testTag("manual_code_input")
                    )
                }

                // SSL toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "Use Secure WSS (TLS)",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "Self-signed LAN certificates trusted",
                            color = Color(0xFF94A3B8),
                            fontSize = 11.sp
                        )
                    }
                    Switch(
                        checked = useSsl,
                        onCheckedChange = { useSsl = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = NavyDark,
                            checkedTrackColor = CyanAccent
                        )
                    )
                }

                errorText?.let {
                    Text(
                        text = it,
                        color = Color(0xFFF43F5E),
                        fontSize = 12.sp
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val trimmedIp = ip.trim()
                    val portNum = port.toIntOrNull() ?: 8443
                    if (trimmedIp.isEmpty() || !trimmedIp.contains(".")) {
                        errorText = "Please enter a valid IPv4 address (e.g. 192.168.1.100)"
                        return@Button
                    }
                    onConnect(
                        ConnectionConfig(
                            ip = trimmedIp,
                            port = portNum,
                            code = code.trim(),
                            useSsl = useSsl
                        )
                    )
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = CyanPrimary,
                    contentColor = NavyDark
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.testTag("manual_connect_submit_button")
            ) {
                Text("Connect")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFF94A3B8))
            ) {
                Text("Cancel")
            }
        }
    )
}
