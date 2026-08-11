package com.exmworkspace.exmwsmail.data.mail

/**
 * Lower bound of the range that a first page is allowed to prune, or null when there is none.
 *
 * After reloading page 1 the cache is cleaned of anything the server no longer lists inside
 * the dates that page covers. The bound therefore has to come from a **real** date: this
 * backend sends `"date": ""` for a few of its own notifications, which map to 0, and a single
 * one of those in the page dragged the bound down to 0 — turning "prune this date range" into
 * "delete everything in the folder that is not on this page". Messages pulled in by any other
 * route, such as a category query reaching further back, vanished on the next refresh.
 *
 * With no usable date at all the answer is null and nothing is pruned: keeping a stale row is
 * a smaller mistake than emptying a folder.
 */
fun pruneWindowStart(dates: List<Long>): Long? = dates.filter { it > 0 }.minOrNull()
