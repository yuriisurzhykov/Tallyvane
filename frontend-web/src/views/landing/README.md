# landing

The public home at `/`. It composes the marketing story from the shared
design-system primitives and app-owned `landing` strings. The product story
centers on career memory: experience connects to evidence for a saved role, then
to one explained next action. A short workflow, capability summary, FAQ, and
registration calls to action support that story.

The page owns its editorial layout in `LandingPage.module.css`; colors,
typography, radii, and spacing come from the shared design tokens. The product
preview is illustrative and local to this view. It does not import the
authentication screen or add a marketing-specific component to
`frontend-shared`.

The content system described in ARCHITECTURE.md §7 / §8.8 is still incomplete.
When `content-kit` can render the public pages, this view can be replaced by
that renderer. The visual rhythm and content structure here should guide the
landing and later blog templates, while the page strings move to CMS content.

The FAQ is a client island because Base UI's compound Accordion is not
available from a server component namespace. The rest of the landing remains
outside that client bundle. `native.tsx` provides semantic landmarks and
heading render callbacks because the project forbids lowercase JSX tags in
view code and because `Text` must receive its merged typography class.
