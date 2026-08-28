package com.clocktower.grimoire.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.clocktower.engine.Character
import com.clocktower.engine.DeathCause
import com.clocktower.engine.DeathEvent
import com.clocktower.engine.DeathNote
import com.clocktower.engine.Deaths
import com.clocktower.engine.GameState
import com.clocktower.engine.KillCause
import com.clocktower.engine.KillOutcome
import com.clocktower.engine.Phase
import com.clocktower.engine.Player
import com.clocktower.engine.StatusEffects
import com.clocktower.engine.Team
import com.clocktower.grimoire.ui.GameViewModel
import com.clocktower.grimoire.ui.components.overlayBottomPadding
import com.clocktower.grimoire.ui.theme.EmberRed
import com.clocktower.grimoire.ui.theme.PoisonGreen
import com.clocktower.grimoire.ui.theme.ShieldBlue

/**
 * THE kill sheet — every path in this app that ends a life renders this one
 * composable (lead D24, friction-log §1).
 *
 * Before WP10 there were five kill paths and only one of them consulted a
 * protection, via a keyword grep over prose that fired "the Soldier is safe
 * from the Demon" when you executed a Soldier — training the storyteller to
 * dismiss the one dialog that mattered. Here:
 *
 * - the cause is CHOSEN, not baked into the button ("Died at night" used to
 *   hard-code `DeathCause.DEMON`);
 * - the preview is `Deaths.killOutcome`, the same 15-step precedence table the
 *   funnel applies, so preview and outcome can never disagree;
 * - protections are split into **Applies to this death** and **Not relevant to
 *   this cause** from `DeathNote.appliesTo`, so a Monk's Safe is visibly
 *   irrelevant to an execution;
 * - "Saved by …" is an ACTION that records — it goes through `Deaths.attempt`,
 *   which writes the prevented death to the ledger and spends the Fool. It is
 *   never a dismiss button that changes nothing.
 *
 * WP8 (night screen) and WP9 (day screen) call this instead of writing their
 * own kill buttons; see the parameter docs for the call shapes.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun KillSheet(
    viewModel: GameViewModel,
    state: GameState,
    targetId: Long,
    /** Pre-selected cause: `EXECUTION` from the day screen, `DEMON_KILL` from a Demon's step. */
    initialCause: DeathCause = defaultCause(state),
    /** True when the caller already knows the cause (an execution is an execution). */
    lockCause: Boolean = false,
    /** Pre-selected killer seat: the Demon whose night step is open. */
    initialKillerId: Long? = null,
    /** Overrides the "<name> dies" title. */
    title: String? = null,
    onDismiss: () -> Unit,
    /** Fired after the funnel ran, with what it decided. */
    onRecorded: (KillOutcome) -> Unit = {},
) {
    val target = state.player(targetId)
    LaunchedEffect(target == null) { if (target == null) onDismiss() }
    if (target == null) return

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        KillSheetBody(
            viewModel = viewModel,
            state = state,
            target = target,
            initialCause = initialCause,
            lockCause = lockCause,
            initialKillerId = initialKillerId,
            title = title,
            onDismiss = onDismiss,
            onRecorded = onRecorded,
        )
    }
}

/** Night deaths at night, storyteller deaths by day, unless the caller says otherwise. */
fun defaultCause(state: GameState): DeathCause =
    if (state.phase == Phase.NIGHT) DeathCause.DEMON_KILL else DeathCause.EXECUTION

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun KillSheetBody(
    viewModel: GameViewModel,
    state: GameState,
    target: Player,
    initialCause: DeathCause,
    lockCause: Boolean,
    initialKillerId: Long?,
    title: String?,
    onDismiss: () -> Unit,
    onRecorded: (KillOutcome) -> Unit,
) {
    var cause by rememberSaveable(target.id) { mutableStateOf(initialCause) }
    var killerId by rememberSaveable(target.id) { mutableStateOf(initialKillerId) }
    var ignoresProtection by rememberSaveable(target.id) { mutableStateOf(false) }
    var uncertain by rememberSaveable(target.id) { mutableStateOf(false) }
    var why by rememberSaveable(target.id) { mutableStateOf("") }

    val killer = killerId?.let { state.player(it) }
    val killCause = KillCause(
        cause = cause,
        sourceCharacterId = killer?.characterId,
        sourcePlayerId = killerId,
        ignoresProtection = ignoresProtection,
        demonKillUncertain = uncertain,
    )
    val outcome = remember(state, target.id, killCause) {
        Deaths.killOutcome(state, viewModel::characterById, target.id, killCause)
    }
    val notes = remember(state, target.id) {
        StatusEffects.notes(state, viewModel::characterById, target.id)
    }
    val (applies, irrelevant) = notes.partition { it.relevantTo(cause) }

    // Death and ruling land in ONE update, so the seat sheet's Undo reverts
    // the whole thing rather than only the note.
    fun apply(optionId: String = "") {
        viewModel.attemptDeath(target.id, killCause, optionId, ruling = why)
        onRecorded(outcome)
        onDismiss()
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(bottom = overlayBottomPadding()),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            title ?: "${target.name} dies",
            style = MaterialTheme.typography.headlineSmall,
        )

        // ---- cause ----
        Text("Cause", style = MaterialTheme.typography.titleSmall)
        for (option in causeOptions(target)) {
            val selected = cause == option.cause
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .clickable(enabled = !lockCause) { cause = option.cause },
            ) {
                RadioButton(
                    selected = selected,
                    onClick = if (lockCause) null else ({ cause = option.cause }),
                )
                Column(Modifier.weight(1f)) {
                    Text(option.label, style = MaterialTheme.typography.bodyLarge)
                    if (option.detail.isNotEmpty()) {
                        Text(
                            option.detail,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        // ---- who killed them ----
        if (cause.wantsKiller) {
            Text("Killed by", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(
                    selected = killerId == null,
                    onClick = { killerId = null },
                    label = { Text("not recorded") },
                )
                for (p in state.seats) {
                    if (p.id == target.id) continue
                    val c = viewModel.characterById(p.characterId)
                    FilterChip(
                        selected = killerId == p.id,
                        onClick = { killerId = p.id },
                        label = { Text("${p.name}${c?.let { " · ${it.name}" } ?: ""}") },
                    )
                }
            }
        }
        if (cause == DeathCause.STORYTELLER) {
            OutlinedTextField(
                value = why,
                onValueChange = { why = it },
                label = { Text("Why (recorded as a ruling)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        HorizontalDivider()

        // ---- what the funnel decided ----
        Text("What happens", style = MaterialTheme.typography.titleSmall)
        OutcomeLine(outcome)

        // ---- protections and triggers, split by whether they bear on THIS cause ----
        if (applies.isNotEmpty()) {
            Text(
                "Applies to this death",
                style = MaterialTheme.typography.titleSmall,
                color = ShieldBlue,
            )
            for (note in applies) NoteLine(note, relevant = true)
        }
        if (irrelevant.isNotEmpty()) {
            Text(
                "Not relevant to this cause",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            for (note in irrelevant) NoteLine(note, relevant = false)
        }

        // ---- overrides ----
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.heightIn(min = 48.dp)) {
            Switch(checked = ignoresProtection, onCheckedChange = { ignoresProtection = it })
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("Nothing can prevent this", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "The Assassin, and any storyteller override.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (cause == DeathCause.DEMON_KILL) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.heightIn(min = 48.dp)) {
                Switch(checked = uncertain, onCheckedChange = { uncertain = it })
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("Uncertain Demon kill", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "Lil' Monsta, Legion, Riot, Yaggababble, Al-Hadikhia — the wiki " +
                            "does not rule whether the Sage and the Grandmother fire.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        HorizontalDivider()

        // ---- the buttons. Every one of them goes through Deaths.attempt. ----
        val choice = outcome as? KillOutcome.Choice
        if (choice != null) {
            Text(choice.question, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            for (option in choice.options) {
                Button(
                    onClick = { apply(option.id) },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                ) { Text(option.label) }
            }
        } else {
            when (outcome) {
                is KillOutcome.Dies -> Button(
                    onClick = { apply() },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                ) { Text("Record the death") }

                is KillOutcome.Prevented -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { apply() },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                    ) { Text(savedByLabel(outcome.by?.sourceCharacterId, viewModel::characterById)) }
                    OutlinedButton(
                        onClick = { ignoresProtection = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("They die anyway") }
                }

                is KillOutcome.Spends -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { apply() },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                    ) {
                        Text(savedByLabel(outcome.sourceId, viewModel::characterById) + " — spends it")
                    }
                    OutlinedButton(
                        onClick = { ignoresProtection = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("They die anyway") }
                }

                is KillOutcome.RegistersDead -> Button(
                    onClick = { apply() },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                ) { Text("They register as dead") }

                is KillOutcome.Redirect -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(outcome.reason, style = MaterialTheme.typography.bodyMedium)
                    for (id in outcome.to) {
                        Button(
                            onClick = {
                                viewModel.attemptDeath(id, killCause)
                                onRecorded(outcome)
                                onDismiss()
                            },
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                        ) { Text("${state.player(id)?.name ?: "?"} dies instead") }
                    }
                    if (!outcome.mandatory) {
                        OutlinedButton(
                            onClick = { apply(Deaths.OPTION_DIES) },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("${target.name} dies after all") }
                    }
                }

                KillOutcome.AlreadyDead -> Text(
                    "${target.name} is already dead — a dead player cannot die again.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                is KillOutcome.Choice -> Unit // handled above
            }
        }
        TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Cancel") }
    }
}

/** The outcome, in the storyteller's own words, above the button that applies it. */
@Composable
private fun OutcomeLine(outcome: KillOutcome) {
    val (text, color) = when (outcome) {
        is KillOutcome.Dies ->
            (outcome.reason.ifEmpty { "Nothing stops it — they die." }) to EmberRed
        is KillOutcome.Prevented ->
            "${outcome.reason}\n${outcome.announce}" to PoisonGreen
        is KillOutcome.Spends ->
            ((outcome.inner as? KillOutcome.Prevented)?.reason ?: "They survive, and it is spent.") to PoisonGreen
        is KillOutcome.RegistersDead -> outcome.reason to MaterialTheme.colorScheme.onSurface
        is KillOutcome.Redirect -> outcome.reason to MaterialTheme.colorScheme.onSurface
        is KillOutcome.Choice -> outcome.question to MaterialTheme.colorScheme.primary
        KillOutcome.AlreadyDead -> "A dead player cannot die again." to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Text(text, style = MaterialTheme.typography.bodyLarge, color = color)
}

@Composable
private fun NoteLine(note: DeathNote, relevant: Boolean) {
    val glyph = if (note.kind == DeathNote.Kind.PROTECTION) "+" else "→"
    Row(modifier = Modifier.fillMaxWidth().padding(start = 4.dp)) {
        Text(
            glyph,
            style = MaterialTheme.typography.bodyMedium,
            color = if (relevant) ShieldBlue else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(6.dp))
        Text(
            note.text,
            style = MaterialTheme.typography.bodyMedium,
            color = if (relevant) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}

private fun savedByLabel(sourceId: String?, lookup: (String?) -> Character?): String {
    val name = sourceId?.let { lookup(it)?.name }
    return if (name == null) "Saved — record it" else "Saved by the $name"
}

/** Which causes want a killer recorded. `DeathRecord` had no killer field at all. */
private val DeathCause.wantsKiller: Boolean
    get() = this == DeathCause.DEMON_KILL ||
        this == DeathCause.EVIL_ABILITY ||
        this == DeathCause.GOOD_ABILITY ||
        this == DeathCause.DAY_ABILITY ||
        this == DeathCause.TRAVELLER_ABILITY

private data class CauseOption(val cause: DeathCause, val label: String, val detail: String = "")

/**
 * All causes reachable from one sheet. Before WP10 three buttons covered five
 * causes and `OTHER_NIGHT_DEATH` / `EXILE` were unreachable from a seat.
 */
private fun causeOptions(target: Player): List<CauseOption> = buildList {
    add(CauseOption(DeathCause.DEMON_KILL, "Demon attack", "Blocked by Monk, Soldier, Innkeeper, Lycanthrope."))
    add(CauseOption(DeathCause.EVIL_ABILITY, "Evil ability", "Assassin, Godfather, Witch, Fearmonger."))
    add(CauseOption(DeathCause.GOOD_ABILITY, "Good ability", "Gossip, Moonchild, Gambler, Tinker."))
    add(CauseOption(DeathCause.EXECUTION, "Execution", "Blocked by the Devil's Advocate, Sailor, Tea Lady, Fool."))
    add(CauseOption(DeathCause.DAY_ABILITY, "Day ability", "Slayer, Psychopath, Golem, Gunslinger."))
    if (target.isTraveller) {
        add(CauseOption(DeathCause.EXILE, "Exile", "Travellers only."))
        add(CauseOption(DeathCause.TRAVELLER_ABILITY, "Traveller ability"))
    }
    add(CauseOption(DeathCause.STORYTELLER, "Storyteller", "A ruling with no character behind it."))
}

/**
 * "executed D3" / "killed N2 by the Pukka" — the fact the seat header, the
 * board row and the history section all used to be missing.
 */
fun deathSummary(state: GameState, lookup: (String) -> Character?, playerId: Long): String {
    val death = state.deaths.lastOrNull { it.playerId == playerId && it.resurrectedAtCycle == null }
        ?: return if (state.player(playerId)?.alive == false) "dead" else "alive"
    return deathLine(death, lookup)
}

/** One death, in the storyteller's words: "killed N2 by the Pukka", "executed D3". */
fun deathLine(death: DeathEvent, lookup: (String) -> Character?): String {
    val stamp = (if (death.atNight) "N" else "D") + death.day
    val by = death.killerCharacterId.takeIf { it.isNotEmpty() }?.let { lookup(it)?.name ?: it }
    val verb = when (death.cause) {
        DeathCause.EXECUTION -> "executed"
        DeathCause.EXILE -> "exiled"
        @Suppress("DEPRECATION") DeathCause.DEMON, DeathCause.DEMON_KILL -> "killed"
        else -> "died"
    }
    return buildString {
        append(verb)
        append(' ')
        append(stamp)
        if (by != null) append(" by the $by")
        if (death.registeredOnly) append(" (registers dead)")
        if (death.resurrectedAtCycle != null) append(" — alive again")
    }
}

/** True when this seat is worth offering an exile rather than an execution. */
internal fun Player.exilable(lookup: (String) -> Character?): Boolean =
    isTraveller || lookup(characterId.orEmpty())?.team == Team.TRAVELLER
