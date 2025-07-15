/*
 * Copyright 2025 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.ai.edge.gallery.ui.common.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun CanCodesDisplay(
  canCodes: List<String>,
  onSendCodes: () -> Unit,
  modifier: Modifier = Modifier,
) {
  var hasShownCodes by remember { mutableStateOf(false) }
  
  // Auto-send codes when component is first displayed
  if (!hasShownCodes && canCodes.isNotEmpty()) {
    hasShownCodes = true
    onSendCodes()
  }

  Card(
    modifier = modifier
      .fillMaxWidth()
      .padding(16.dp),
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ),
    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
  ) {
    Column(
      modifier = Modifier.padding(16.dp),
    ) {
      // Header
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Icon(
          Icons.Rounded.Warning,
          contentDescription = "CAN Codes",
          tint = MaterialTheme.colorScheme.error,
          modifier = Modifier.size(20.dp),
        )
        Text(
          text = "CAN Error Codes Detected",
          style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
          color = MaterialTheme.colorScheme.error,
        )
      }
      
      Spacer(modifier = Modifier.height(12.dp))
      
      // CAN Codes display
      Text(
        text = "The following diagnostic trouble codes have been detected:",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      
      Spacer(modifier = Modifier.height(8.dp))
      
      // Display each CAN code in a styled box
      canCodes.forEach { code ->
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .border(
              width = 1.dp,
              color = MaterialTheme.colorScheme.outline,
              shape = RoundedCornerShape(8.dp),
            )
            .background(
              color = MaterialTheme.colorScheme.surface,
              shape = RoundedCornerShape(8.dp),
            )
            .padding(12.dp),
        ) {
          Text(
            text = code,
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
            color = MaterialTheme.colorScheme.primary,
          )
        }
      }
      
      Spacer(modifier = Modifier.height(16.dp))
      
      // Send button
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
      ) {
        IconButton(
          onClick = onSendCodes,
          colors = IconButtonDefaults.iconButtonColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
          ),
        ) {
          Icon(
            Icons.AutoMirrored.Rounded.Send,
            contentDescription = "Analyze CAN Codes",
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.size(20.dp),
          )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
          text = "Analyze Codes",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.padding(top = 12.dp),
        )
      }
    }
  }
} 