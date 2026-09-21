package kniezrec.com.flightinfo.ui.route

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kniezrec.com.flightinfo.R
import kniezrec.com.flightinfo.nearby.NearbyCityRecord
import kniezrec.com.flightinfo.route.RouteEndpoint

@Composable
fun RoutePicker(endpoint: RouteEndpoint, initial: NearbyCityRecord?, results: List<NearbyCityRecord>, loading: Boolean, error: String?, onSearch: (String) -> Unit, onConfirm: (NearbyCityRecord) -> Unit, onCancel: () -> Unit, onRetry: () -> Unit) {
    var query by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf(initial) }
    LaunchedEffect(initial) { selected = initial }
    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(if (endpoint == RouteEndpoint.DEPARTURE) R.string.route_picker_departure else R.string.route_picker_destination))
        OutlinedTextField(value = query, onValueChange = { query = it }, modifier = Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.route_city_name)) }, singleLine = false)
        Button(onClick = { onSearch(query) }, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.route_search)) }
        Text(stringResource(R.string.route_map_instruction))
        if (loading) Text(stringResource(R.string.route_searching))
        if (error != null) { Text(stringResource(R.string.route_error)); TextButton(onClick = onRetry, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.route_retry)) } }
        if (!loading && error == null && query.isNotBlank() && results.isEmpty()) Text(stringResource(R.string.route_no_cities))
        LazyColumn(Modifier.weight(1f)) { items(results) { city -> TextButton(onClick = { selected = city }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("${city.name} (${city.country})") } } }
        selected?.let { Text("Selected: ${it.name} (${it.country})") }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onCancel, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.route_cancel)) }
            Button(onClick = { selected?.let(onConfirm) }, enabled = selected != null, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.route_confirm)) }
        }
    }
}
