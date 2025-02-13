package org.XXXX-1.evaluation.execution

import java.io.Serializable


class CherryPick(
    val id: Int, val cherryCommit: String, val cherryParentCommit: String,
    val targetCommit: String, val expectedResultCommit: String,
    val isTrivial: Boolean,
): Serializable