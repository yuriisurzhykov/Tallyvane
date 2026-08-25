package tallyvane.platform.kernel

/**
 * Marks one action a user can perform: sign in, sign out, upload a document,
 * delete a contact.
 *
 * The granularity is the point. A use case is a thing somebody does, not a step
 * inside how it is done — "sign in" is one, "complete the OAuth exchange" is not,
 * because the second names a mechanism rather than an intent.
 *
 * ### The shape
 *
 * A use case is published as an interface and carries its implementation nested
 * inside it. Consumers receive the interface; the composition root is the only
 * place that names the concrete type.
 *
 * ```
 * public interface SignInUseCase : UseCase {
 *     public suspend fun signIn(request: SignInRequest): SignInOutcome
 *
 *     public class SignIn(
 *         private val users: Users,
 *         private val sessions: Sessions,
 *         private val transactions: TransactionRunner,
 *         private val clock: Clock,
 *     ) : SignInUseCase {
 *         override suspend fun signIn(request: SignInRequest): SignInOutcome = TODO()
 *     }
 * }
 * ```
 *
 * The interface is named for the action with a `UseCase` suffix; the nested class
 * is named for the action alone. The method is the action's verb, so a call site
 * reads `signIn.signIn(request)` — the same shape as `parser.parse()`, where the
 * type carries the subject and the method carries the operation. `invoke` is
 * forbidden: an `operator` call takes its only meaning from the field name, which
 * the consumer chooses, so two unrelated use cases read alike under review.
 *
 * Nesting is allowed here although an adapter may never nest on its port, and the
 * difference is not a matter of taste. That ban exists because "a nested type
 * compiles into the module that owns the interface", which would carry a driver
 * into a driver-free module — and a use case's interface and implementation belong
 * to the same `application` layer of the same module, so there is no boundary for
 * anything to cross. When a second implementation appears, both move out and take
 * names that say by what means.
 *
 * ### Why this is not in `domain`
 *
 * A use case holds ports, opens a transaction and reads the clock; `domain`
 * forbids all three. In Clean Architecture's terms `domain` is the Entities ring
 * and this is the Use Cases ring — both business logic, one of them pure. And
 * mechanically there is no shared `domain` to put a marker in: every module has
 * its own, while `modules.yaml` lets an `application` layer see `platform:kernel`.
 *
 * Full record: ADR-053.
 */
public interface UseCase
