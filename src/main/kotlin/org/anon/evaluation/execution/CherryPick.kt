package org.anon.evaluation.execution

import java.io.Serializable


class CherryPick(
    val id: Int, val cherryCommit: String, val cherryParentCommit: String,
    val targetCommit: String, val expectedResultCommit: String,
    val isTrivial: Boolean,
): Serializable