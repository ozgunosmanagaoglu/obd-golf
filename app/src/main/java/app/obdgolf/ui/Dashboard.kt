package app.obdgolf.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import app.obdgolf.LinkState
import app.obdgolf.core.Alarm
import app.obdgolf.core.CarData
import app.obdgolf.core.Dashboard
import app.obdgolf.core.DashboardPage
import app.obdgolf.core.Gauge
import app.obdgolf.core.GaugeSize
import app.obdgolf.core.GaugeStyle
import app.obdgolf.core.GridPacker
import app.obdgolf.core.Metric
import app.obdgolf.core.Preset
import app.obdgolf.core.Str
import kotlinx.coroutines.launch

/** Tile grid columns: 2 on a phone in portrait, 4 from 600 dp (tablets, landscape phones, head units). */
fun gridColumns(width: Dp): Int = if (width >= 600.dp) 4 else 2

/**
 * The customizable dashboard: pages of tiles, swiped left and right.
 * [carScreen] is the landscape / head-unit variant: bigger touch targets and values, no tab bar around it.
 * Every edit goes straight to [onChange], which saves it.
 */
@Composable
fun DashboardScreen(
    d: CarData,
    link: LinkState,
    dash: Dashboard,
    history: Map<String, List<Double>>,
    onLinkClick: () -> Unit,
    onChange: (Dashboard) -> Unit,
    carScreen: Boolean = false,
    startEditing: Boolean = false,
) {
    var editing by rememberSaveable { mutableStateOf(startEditing) }
    // (page, tile) being edited in the sheet; tile -1 means "add a tile to this page".
    var sheet by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    val pager = rememberPagerState { dash.pages.size }
    val scope = rememberCoroutineScope()
    val alarms = dash.alarms(d)
    AlarmHaptics(alarms)

    Column(
        Modifier.fillMaxSize().padding(horizontal = if (carScreen) Space.xl else Space.l, vertical = Space.m),
        verticalArrangement = Arrangement.spacedBy(Space.m),
    ) {
        DashHeader(link, editing, carScreen, onLinkClick) { editing = !editing; sheet = null }
        when {
            editing -> EditBar(
                dash, pager.currentPage, carScreen, onChange,
                onAddTile = { sheet = pager.currentPage to -1 },
                onAddPage = {
                    onChange(dash.copy(pages = dash.pages + DashboardPage(emptyList())))
                    scope.launch { pager.animateScrollToPage(dash.pages.size) }
                },
            )
            alarms.isNotEmpty() -> AlarmBanner(alarms)
            link is LinkState.Disconnected || link is LinkState.Failed -> OfflineNote()
        }
        HorizontalPager(
            pager,
            Modifier.weight(1f).fillMaxWidth(),
            pageSpacing = Space.l,
            // While editing, a long press drags tiles; pages are switched with the dots instead.
            userScrollEnabled = !editing,
            verticalAlignment = Alignment.Top,
        ) { i ->
            val page = dash.pages.getOrNull(i) ?: return@HorizontalPager
            DashPage(
                page, d, history, editing, carScreen,
                onMove = { from, to -> onChange(dash.updatePage(i) { it.move(from, to - from) }) },
                onTileClick = { t -> sheet = i to t },
                onAddTile = { sheet = i to -1 },
            )
        }
        if (dash.pages.size > 1) PageDots(dash.pages.size, pager.currentPage) { scope.launch { pager.animateScrollToPage(it) } }
    }

    sheet?.let { (p, t) ->
        TileSheet(
            dash, p, t, onChange,
            onPicked = { newIndex -> sheet = p to newIndex },
            onDismiss = { sheet = null },
        )
    }
}

@Composable
private fun DashHeader(link: LinkState, editing: Boolean, carScreen: Boolean, onLinkClick: () -> Unit, onEdit: () -> Unit) {
    val touch = if (carScreen) Touch.car else Touch.min
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.s)) {
        Badge(Brand.BADGE)
        Spacer(Modifier.weight(1f))
        LinkChip(link, onLinkClick)
        TextButton(
            onClick = onEdit,
            modifier = Modifier.heightIn(min = touch).widthIn(min = touch),
            shape = RoundedCornerShape(Radius.chip),
        ) {
            Icon(if (editing) Icons.Filled.Check else Icons.Filled.Edit, contentDescription = null, tint = if (editing) C.Red else C.Muted)
            Spacer(Modifier.width(Space.xs))
            Text(s(if (editing) Str.DASH_DONE else Str.DASH_EDIT), style = Type.label, color = C.Text, maxLines = 1)
        }
    }
}

/** One short vibration when a value newly enters its warning zone, not on every update. */
@Composable
private fun AlarmHaptics(alarms: List<Alarm>) {
    val haptic = LocalHapticFeedback.current
    val ids = alarms.map { it.metric.id }.toSet()
    var known by remember { mutableStateOf(emptySet<String>()) }
    LaunchedEffect(ids) {
        if ((ids - known).isNotEmpty()) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        known = ids
    }
}

/** Red strip above the tiles: what is wrong, in words, with the value. TalkBack announces it. */
@Composable
private fun AlarmBanner(alarms: List<Alarm>) {
    val a = alarms.first()
    val u = LocalUnits.current
    val (v, unit) = u.convert(a.value, a.metric.unit)
    val text = buildString {
        append(metricLabel(a.metric)).append(" · ")
        append(s(if (a.above) Str.GAUGE_HIGH else Str.GAUGE_LOW).uppercase(LocalStrings.current.lang.locale))
        append(" · ").append(v.fmt(u.decimalsFor(a.metric.unit, a.metric.decimals)))
        if (unit.isNotEmpty()) append(" ").append(unit)
        if (alarms.size > 1) append("  +").append(alarms.size - 1)
    }
    Row(
        Modifier.fillMaxWidth().heightIn(min = Touch.min).clip(RoundedCornerShape(Radius.chip)).background(C.Alarm)
            .padding(horizontal = Space.l, vertical = Space.s)
            .semantics { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.s),
    ) {
        Text("!", style = Type.title, color = C.OnAccent)
        Text(text, style = Type.body, color = C.OnAccent, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun OfflineNote() = Row(
    Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.chip)).background(C.Card).padding(horizontal = Space.l, vertical = Space.m),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(Space.s),
) {
    StatusDot(C.Dim)
    Text(s(Str.DASH_OFFLINE), style = Type.body, color = C.Muted)
}

/** Dots under the pages; each is a 48 dp target that jumps to its page. */
@Composable
private fun PageDots(count: Int, current: Int, onSelect: (Int) -> Unit) = Row(
    Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.Center,
) {
    val strings = LocalStrings.current
    repeat(count) { i ->
        Box(
            Modifier.size(Touch.min).clickable { onSelect(i) }
                .semantics { contentDescription = strings.format(Str.DASH_PAGE, i + 1, count) },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier.height(8.dp).width(if (i == current) 22.dp else 8.dp).clip(CircleShape)
                    .background(if (i == current) C.Text else C.Track)
            )
        }
    }
}

private data class Drag(val index: Int, val offset: Offset)

/**
 * One page laid out by [GridPacker]. Rows share the height so a page fits the screen without scrolling;
 * below a readable minimum (small phone, large fonts) the page scrolls instead of squeezing the values.
 */
@Composable
private fun DashPage(
    page: DashboardPage,
    d: CarData,
    history: Map<String, List<Double>>,
    editing: Boolean,
    carScreen: Boolean,
    onMove: (Int, Int) -> Unit,
    onTileClick: (Int) -> Unit,
    onAddTile: () -> Unit,
) {
    if (page.gauges.isEmpty()) {
        EmptyPage(editing, onAddTile)
        return
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val haptic = LocalHapticFeedback.current
        val cols = gridColumns(maxWidth)
        val gap = Space.m
        val cells = remember(page, cols) { GridPacker.pack(page.gauges.map { it.size }, cols) }
        val rows = GridPacker.rows(cells).coerceAtLeast(1)
        val cellW = (maxWidth - gap * (cols - 1)) / cols
        val minRow = (if (carScreen) 132.dp else 120.dp) * density.fontScale.coerceAtLeast(1f)
        val fit = (maxHeight - gap * (rows - 1)) / rows
        val rowH = fit.coerceIn(minRow, maxOf(minRow, cellW * 1.3f))
        val total = rowH * rows + gap * (rows - 1)

        fun x(col: Int) = (cellW + gap) * col
        fun y(row: Int) = (rowH + gap) * row
        fun w(n: Int) = cellW * n + gap * (n - 1)
        fun h(n: Int) = rowH * n + gap * (n - 1)
        val rects = with(density) {
            cells.map { c -> Rect(x(c.col).toPx(), y(c.row).toPx(), (x(c.col) + w(c.cols)).toPx(), (y(c.row) + h(c.rows)).toPx()) }
        }
        var drag by remember { mutableStateOf<Drag?>(null) }

        Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Box(Modifier.fillMaxWidth().height(total)) {
                page.gauges.forEachIndexed { i, g ->
                    val c = cells[i]
                    val dragged = drag?.takeIf { it.index == i }
                    key(i) {
                        val strings = LocalStrings.current
                        Box(
                            Modifier.offset(x(c.col), y(c.row)).size(w(c.cols), h(c.rows))
                                .zIndex(if (dragged != null) 1f else 0f)
                                .graphicsLayer {
                                    translationX = dragged?.offset?.x ?: 0f
                                    translationY = dragged?.offset?.y ?: 0f
                                    val scale = if (dragged != null) 1.04f else 1f
                                    scaleX = scale; scaleY = scale
                                }
                                .then(
                                    if (editing) Modifier
                                        .pointerInput(i, rects) {
                                            detectDragGesturesAfterLongPress(
                                                onDragStart = {
                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    drag = Drag(i, Offset.Zero)
                                                },
                                                onDrag = { change, amount ->
                                                    change.consume()
                                                    drag = drag?.let { it.copy(offset = it.offset + amount) }
                                                },
                                                onDragEnd = {
                                                    drag?.let { dr ->
                                                        val to = rects.indexOfFirst { it.contains(rects[i].center + dr.offset) }
                                                        if (to >= 0 && to != i) onMove(i, to)
                                                    }
                                                    drag = null
                                                },
                                                onDragCancel = { drag = null },
                                            )
                                        }
                                        .clickable(onClickLabel = strings[Str.DASH_EDIT]) { onTileClick(i) }
                                    else Modifier
                                ),
                        ) {
                            GaugeTile(g, d, history[g.metric.id].orEmpty(), Modifier.fillMaxSize(), carScreen)
                            if (editing) EditMarker(Modifier.align(Alignment.TopEnd))
                        }
                    }
                }
            }
        }
    }
}

/** Dashed-looking outline and a pencil, so edit mode is obvious at a glance. */
@Composable
private fun EditMarker(modifier: Modifier) {
    Box(Modifier.fillMaxSize().border(2.dp, C.Muted.copy(alpha = 0.5f), RoundedCornerShape(Radius.tile)))
    Box(
        modifier.padding(Space.s).size(28.dp).clip(CircleShape).background(C.Raised),
        contentAlignment = Alignment.Center,
    ) { Icon(Icons.Filled.Edit, contentDescription = null, tint = C.Text, modifier = Modifier.size(16.dp)) }
}

@Composable
private fun EmptyPage(editing: Boolean, onAddTile: () -> Unit) = Column(
    Modifier.fillMaxSize().padding(Space.xl),
    verticalArrangement = Arrangement.spacedBy(Space.l, Alignment.CenterVertically),
    horizontalAlignment = Alignment.CenterHorizontally,
) {
    Text(s(Str.DASH_EMPTY), style = Type.body, color = C.Muted, textAlign = TextAlign.Center)
    if (editing) {
        OutlinedButton(onClick = onAddTile, modifier = Modifier.heightIn(min = Touch.min)) {
            Icon(Icons.Filled.Add, contentDescription = null, tint = C.Text)
            Spacer(Modifier.width(Space.xs))
            Text(s(Str.DASH_ADD_TILE), color = C.Text)
        }
    }
}

/** Shown instead of the alarm strip while editing: layout presets and page actions. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EditBar(
    dash: Dashboard,
    page: Int,
    carScreen: Boolean,
    onChange: (Dashboard) -> Unit,
    onAddTile: () -> Unit,
    onAddPage: () -> Unit,
) {
    val strings = LocalStrings.current
    val touch = if (carScreen) Touch.car else Touch.min
    Column(verticalArrangement = Arrangement.spacedBy(Space.s)) {
        Text(s(Str.DASH_LAYOUT), style = Type.label, color = C.Muted)
        // Picking a preset starts over from it; the current one stays selected.
        Segmented(Preset.entries, dash.preset, { strings[it.label] }) { onChange(it.dashboard) }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.s), verticalArrangement = Arrangement.spacedBy(Space.s)) {
            EditAction(Str.DASH_ADD_TILE, touch, Icons.Filled.Add, onClick = onAddTile)
            EditAction(Str.DASH_ADD_PAGE, touch, Icons.Filled.Add, onClick = onAddPage)
            if (dash.pages.size > 1) {
                EditAction(Str.DASH_REMOVE_PAGE, touch) {
                    onChange(dash.copy(pages = dash.pages.filterIndexed { i, _ -> i != page }))
                }
            }
            EditAction(Str.DASH_RESET, touch, Icons.Filled.Refresh) { onChange(dash.preset.dashboard) }
        }
        Text(s(Str.DASH_DRAG_HINT), style = Type.caption, color = C.Dim)
    }
}

@Composable
private fun EditAction(label: Str, touch: Dp, icon: androidx.compose.ui.graphics.vector.ImageVector? = null, onClick: () -> Unit) =
    OutlinedButton(onClick = onClick, modifier = Modifier.heightIn(min = touch), shape = RoundedCornerShape(Radius.chip)) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = C.Text, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(Space.xs))
        }
        Text(s(label), style = Type.label, color = C.Text, maxLines = 1)
    }

/**
 * Bottom sheet for one tile: which value, how it is drawn, how big, warnings on/off, order, remove.
 * Every change applies at once, so the tile behind the sheet is the preview.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun TileSheet(
    dash: Dashboard,
    pageIndex: Int,
    tile: Int,
    onChange: (Dashboard) -> Unit,
    onPicked: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val page = dash.pages.getOrNull(pageIndex)
    val g = page?.gauges?.getOrNull(tile)
    if (page == null || (tile >= 0 && g == null)) {
        // The page or tile went away (e.g. reset while the sheet was open).
        LaunchedEffect(Unit) { onDismiss() }
        return
    }
    val strings = LocalStrings.current
    fun set(f: (DashboardPage) -> DashboardPage) = onChange(dash.updatePage(pageIndex, f))

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = C.Raised,
    ) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = Space.xl).padding(bottom = Space.xxl),
            verticalArrangement = Arrangement.spacedBy(Space.m),
        ) {
            Text(g?.let { metricLabel(it.metric) } ?: s(Str.DASH_ADD_TILE), style = Type.headline, color = C.Text)

            if (g != null) {
                Text(s(Str.DASH_STYLE), style = Type.label, color = C.Muted)
                Segmented(GaugeStyle.entries, g.style, { strings[styleLabel(it)] }) { st -> set { it.replace(tile, g.copy(style = st)) } }
                Text(s(Str.DASH_SIZE), style = Type.label, color = C.Muted)
                Segmented(GaugeSize.entries, g.size, { "${it.columns}×${it.rows}" }) { sz -> set { it.replace(tile, g.copy(size = sz)) } }
                Row(Modifier.fillMaxWidth().heightIn(min = Touch.min), verticalAlignment = Alignment.CenterVertically) {
                    Text(s(Str.DASH_WARNINGS), style = Type.body, color = C.Text, modifier = Modifier.weight(1f))
                    Switch(g.alarms, { on -> set { it.replace(tile, g.copy(alarms = on)) } }, colors = SwitchDefaults.colors(checkedTrackColor = C.Red))
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.s), verticalArrangement = Arrangement.spacedBy(Space.s)) {
                    if (tile > 0) EditAction(Str.DASH_MOVE_EARLIER, Touch.min) { set { it.move(tile, -1) }; onPicked(tile - 1) }
                    if (tile < page.gauges.lastIndex) EditAction(Str.DASH_MOVE_LATER, Touch.min) { set { it.move(tile, 1) }; onPicked(tile + 1) }
                    EditAction(Str.DASH_REMOVE_TILE, Touch.min) { set { it.remove(tile) }; onDismiss() }
                }
            }

            Text(s(Str.DASH_PICK_VALUE), style = Type.label, color = C.Muted)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.s), verticalArrangement = Arrangement.spacedBy(Space.s)) {
                Metric.all.forEach { m ->
                    val on = g?.metric == m
                    Text(
                        metricLabel(m),
                        style = Type.label,
                        color = if (on) C.OnAccent else C.Text,
                        modifier = Modifier.clip(RoundedCornerShape(Radius.chip))
                            .background(if (on) C.Red else C.Card)
                            .clickable {
                                if (g == null) {
                                    set { it.add(Gauge(m)) }
                                    onPicked(page.gauges.size)
                                } else set { it.replace(tile, g.copy(metric = m, alarmAbove = null, alarmBelow = null)) }
                            }
                            .heightIn(min = Touch.min).padding(horizontal = Space.m, vertical = 14.dp),
                    )
                }
            }
        }
    }
}

private fun styleLabel(st: GaugeStyle) = when (st) {
    GaugeStyle.DIGITAL -> Str.STYLE_DIGITAL
    GaugeStyle.ARC -> Str.STYLE_ARC
    GaugeStyle.BAR -> Str.STYLE_BAR
    GaugeStyle.SPARKLINE -> Str.STYLE_SPARKLINE
}
