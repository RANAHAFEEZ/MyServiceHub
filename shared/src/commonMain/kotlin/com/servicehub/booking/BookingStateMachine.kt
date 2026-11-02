package com.servicehub.booking

enum class BookingStatus {
    REQUESTED, ACCEPTED, DECLINED, EXPIRED, ON_THE_WAY, IN_PROGRESS,
    COMPLETED, PAID, REVIEWED, CANCELLED, DISPUTED
}

enum class Actor { CONSUMER, PROVIDER, ADMIN, SYSTEM }

sealed interface TransitionResult {
    data class Ok(val status: BookingStatus) : TransitionResult
    data class Rejected(val reason: String) : TransitionResult
}

/**
 * The single place that decides who may move a booking to which status.
 * The backend must enforce the same rules (never trust the app alone).
 */
object BookingStateMachine {

    private data class Rule(val from: BookingStatus, val to: BookingStatus, val actors: Set<Actor>)

    private val rules = listOf(
        Rule(BookingStatus.REQUESTED, BookingStatus.ACCEPTED, setOf(Actor.PROVIDER)),
        Rule(BookingStatus.REQUESTED, BookingStatus.DECLINED, setOf(Actor.PROVIDER)),
        Rule(BookingStatus.REQUESTED, BookingStatus.EXPIRED, setOf(Actor.SYSTEM)),
        Rule(BookingStatus.REQUESTED, BookingStatus.CANCELLED, setOf(Actor.CONSUMER, Actor.ADMIN)),

        Rule(BookingStatus.ACCEPTED, BookingStatus.ON_THE_WAY, setOf(Actor.PROVIDER)),
        Rule(BookingStatus.ACCEPTED, BookingStatus.CANCELLED, setOf(Actor.CONSUMER, Actor.PROVIDER, Actor.ADMIN)),

        Rule(BookingStatus.ON_THE_WAY, BookingStatus.IN_PROGRESS, setOf(Actor.PROVIDER)),
        Rule(BookingStatus.ON_THE_WAY, BookingStatus.CANCELLED, setOf(Actor.CONSUMER, Actor.PROVIDER, Actor.ADMIN)),

        Rule(BookingStatus.IN_PROGRESS, BookingStatus.COMPLETED, setOf(Actor.PROVIDER)),
        Rule(BookingStatus.IN_PROGRESS, BookingStatus.DISPUTED, setOf(Actor.CONSUMER, Actor.ADMIN)),

        Rule(BookingStatus.COMPLETED, BookingStatus.PAID, setOf(Actor.CONSUMER, Actor.PROVIDER, Actor.ADMIN, Actor.SYSTEM)),
        Rule(BookingStatus.COMPLETED, BookingStatus.DISPUTED, setOf(Actor.CONSUMER, Actor.ADMIN)),

        Rule(BookingStatus.PAID, BookingStatus.REVIEWED, setOf(Actor.CONSUMER)),
        Rule(BookingStatus.PAID, BookingStatus.DISPUTED, setOf(Actor.CONSUMER, Actor.ADMIN)),

        // Admin settles a dispute
        Rule(BookingStatus.DISPUTED, BookingStatus.COMPLETED, setOf(Actor.ADMIN)),
        Rule(BookingStatus.DISPUTED, BookingStatus.PAID, setOf(Actor.ADMIN)),
        Rule(BookingStatus.DISPUTED, BookingStatus.CANCELLED, setOf(Actor.ADMIN)),
    )

    fun transition(from: BookingStatus, to: BookingStatus, actor: Actor): TransitionResult {
        val rule = rules.firstOrNull { it.from == from && it.to == to }
            ?: return TransitionResult.Rejected("A booking cannot go from $from to $to")
        if (actor !in rule.actors) return TransitionResult.Rejected("$actor cannot move a booking from $from to $to")
        return TransitionResult.Ok(to)
    }

    /** Buttons to show this actor for a booking in this status. */
    fun allowedNext(from: BookingStatus, actor: Actor): List<BookingStatus> =
        rules.filter { it.from == from && actor in it.actors }.map { it.to }
}
