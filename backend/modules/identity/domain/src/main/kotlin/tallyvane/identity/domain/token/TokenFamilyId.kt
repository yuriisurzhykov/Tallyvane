package tallyvane.identity.domain.token

import kotlin.uuid.Uuid

/**
 * One refresh token's lineage. Every rotation of the same session's refresh token shares this id,
 * which is what will let a refresh-rotation policy recognise "this session's chain" independently
 * of which specific token in it was just presented.
 */
@JvmInline
public value class TokenFamilyId(public val value: Uuid)
