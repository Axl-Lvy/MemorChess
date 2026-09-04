package proj.memorchess.axl.test_util

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import proj.memorchess.axl.core.data.DatabaseQueryManager
import proj.memorchess.axl.core.graph.TreeStore
import proj.memorchess.axl.core.sync.DeviceIdentity

/**
 * Builds a [TreeStore] over [database] with a default background prefetch scope and a fresh
 * ephemeral [DeviceIdentity] for tests.
 *
 * Mirrors the production wiring (a [SupervisorJob] on [Dispatchers.Default]) without forcing every
 * test to spell out the scope. Tests that need deterministic, joinable prefetch pass an explicit
 * scope to the [TreeStore] constructor instead.
 */
fun testTreeStore(database: DatabaseQueryManager): TreeStore =
  TreeStore(database, CoroutineScope(SupervisorJob() + Dispatchers.Default), DeviceIdentity.ephemeral())
