# The product frame and the home

The signed-in app follows the design canvas ("Tallyvane — product design", variant E): one calm column,
the wordmark above, the "what's new?" field pinned below. It is not a console, so it has no sidebar.

## Pieces

| Piece | Where | What it is |
|---|---|---|
| `ProductShell` | `frontend-shared/ui/product-shell` | Header (way back, wordmark, actions), one centred column, optional sticky dock. Widths are layout roles `--layout-column-max-width` (45rem) and `--layout-column-wide-max-width` (60rem). |
| `Track` | `frontend-shared/ui/track` | The route as stations on a line: `done`, `now`, `ahead`, `end`. Shape and size carry the state, not only colour. |
| `Composer` | `frontend-shared/ui/composer` | The pill pinned below. Enter sends, Shift+Enter starts a line. The only pill in the system. |
| `Heading`, `Icon` | `frontend-shared/ui/heading`, `.../icon` | A real heading at a stated level; the icon set behind one component. |
| `ProductFrame` | `frontend-app/src/widgets/product-frame` | `ProductShell` with the app's wordmark, résumé shortcut and a way back. The account menu is passed in by the page, because a widget cannot import another widget. |

`AppShell` (sidebar and title bar) stays for the admin app.

## The home

Route to the goal, then the one main step, then the stories. After the person tells something, "Here is what I
kept" shows the facts taken from it with the words each quotes, until they press Done. Nothing on the home
suggests a vacancy (ADR-040), and nothing is drawn that has no data behind it: the practice, think and learn
cards of the canvas wait for the features that feed them.

## Admin: Models

One card for the provider in use; the others in a quiet list with the same two buttons (Use, Change);
removing lives inside the edit form and asks twice. Below it, the last 30 days of cost by provider, model and
spec.
