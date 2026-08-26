package konsist.fixtures.failuregroupsunderroot

interface Failure

// A loose failure at the top level of a file: nothing groups it, so a mapping table for this
// module would need one entry point per case, and a forgotten one becomes an unpredicted 500.
data class RangeInvalid(val min: Int, val max: Int) : Failure
