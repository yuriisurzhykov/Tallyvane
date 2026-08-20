# Component authoring — patterns

Full examples backing the rules in [SKILL.md](SKILL.md). Read the relevant
section only when implementing that pattern.

## 1. Explicit variants over boolean props

```tsx
// ❌ N booleans is 2^N states, most of them invalid, and unreadable at the
// call site — what does this actually render?
function Composer({
  onSubmit,
  isThread,
  channelId,
  isDMThread,
  dmId,
  isEditing,
  isForwarding,
}: Props) {
  return (
    <form>
      <Header />
      <Input />
      {isDMThread ? (
        <AlsoSendToDMField id={dmId} />
      ) : isThread ? (
        <AlsoSendToChannelField id={channelId} />
      ) : null}
      {isEditing ? <EditActions /> : isForwarding ? <ForwardActions /> : <DefaultActions />}
      <Footer onSubmit={onSubmit} />
    </form>
  );
}
```

```tsx
// ✅ each variant is explicit about what it renders; they share internals
// without sharing one monolithic parent
function ThreadComposer({ channelId }: { channelId: string }) {
  return (
    <ThreadProvider channelId={channelId}>
      <Composer.Frame>
        <Composer.Input />
        <AlsoSendToChannelField channelId={channelId} />
        <Composer.Footer>
          <Composer.Formatting />
          <Composer.Submit />
        </Composer.Footer>
      </Composer.Frame>
    </ThreadProvider>
  );
}

function EditMessageComposer({ messageId }: { messageId: string }) {
  return (
    <EditMessageProvider messageId={messageId}>
      <Composer.Frame>
        <Composer.Input />
        <Composer.Footer>
          <Composer.CancelEdit />
          <Composer.SaveEdit />
        </Composer.Footer>
      </Composer.Frame>
    </EditMessageProvider>
  );
}
```

## 2. Compound components with a context contract

Split the context value into three parts — `state`, `actions`, `meta` — so
every sub-part depends on that interface, never on a concrete hook. Any
provider that implements the interface works with the same UI parts.

```tsx
interface ComposerState {
  input: string;
  attachments: Attachment[];
}

interface ComposerActions {
  update: (updater: (state: ComposerState) => ComposerState) => void;
  submit: () => void;
}

interface ComposerMeta {
  inputRef: React.RefObject<HTMLTextAreaElement>;
}

interface ComposerContextValue {
  state: ComposerState;
  actions: ComposerActions;
  meta: ComposerMeta;
}

const ComposerContext = createContext<ComposerContextValue | null>(null);

function useComposerContext() {
  const ctx = use(ComposerContext);
  if (!ctx) {
    throw new Error("Composer.* must be used inside <Composer.Root>");
  }
  return ctx;
}

function ComposerRoot({ state, actions, meta, children }: ComposerContextValue & { children: React.ReactNode }) {
  return <ComposerContext value={{ state, actions, meta }}>{children}</ComposerContext>;
}

function ComposerInput() {
  const { state, actions, meta } = useComposerContext();
  return (
    <textarea
      ref={meta.inputRef}
      value={state.input}
      onChange={(e) => actions.update((s) => ({ ...s, input: e.target.value }))}
    />
  );
}

function ComposerSubmit() {
  const { actions } = useComposerContext();
  return <Button onClick={actions.submit}>Send</Button>;
}

const Composer = { Root: ComposerRoot, Input: ComposerInput, Submit: ComposerSubmit };
```

Two providers, same UI — the provider is the only place that knows whether
state is local or server-synced:

```tsx
// Local, ephemeral form state
function ForwardMessageProvider({ children }: { children: React.ReactNode }) {
  const [state, setState] = useState<ComposerState>({ input: "", attachments: [] });
  const forwardMessage = useForwardMessage();
  const inputRef = useRef<HTMLTextAreaElement>(null);

  return (
    <Composer.Root
      state={state}
      actions={{ update: setState, submit: forwardMessage }}
      meta={{ inputRef }}
    >
      {children}
    </Composer.Root>
  );
}

// Global, server-synced state — same Composer.Input, Composer.Submit work unchanged
function ChannelProvider({ channelId, children }: { channelId: string; children: React.ReactNode }) {
  const { state, update, submit } = useGlobalChannel(channelId);
  const inputRef = useRef<HTMLTextAreaElement>(null);

  return (
    <Composer.Root state={state} actions={{ update, submit }} meta={{ inputRef }}>
      {children}
    </Composer.Root>
  );
}
```

**Key insight:** components that need shared state don't have to be nested
inside each other visually — they only need to be within the same provider.
A `ForwardButton` sitting next to the composer in a dialog footer, not inside
`Composer.Root`'s children tree but still within its provider boundary, can
still call `actions.submit()`.

Naming convention in this codebase: mirror Base UI's own parts (`Root`,
`Trigger`, `Popup`) rather than inventing `Provider`/`Frame` naming, so a
`DataTable.Root` composed next to a Base UI `Menu.Root` in the same tree
reads consistently.

## 3. Render-prop polymorphism

Modeled on Base UI's `useRender` (`@base-ui/react/use-render`):

```tsx
import { useRender } from "@base-ui/react/use-render";

interface ButtonProps extends useRender.ComponentProps<"button"> {
  nativeButton?: boolean;
}

function Button({ render, nativeButton = true, ...props }: ButtonProps) {
  const defaultProps: useRender.ElementProps<"button"> = {
    className: styles.button,
    ...(nativeButton ? { type: "button" as const } : {}),
  };

  return useRender({
    defaultTagName: "button",
    render,
    props: mergeProps<"button">(defaultProps, props),
  });
}
```

```tsx
// Element form
<Button render={<a href="/jobs" />} nativeButton={false}>View jobs</Button>

// Function form — needed when the rendered content depends on internal state
<Switch.Thumb
  render={(props, state) => (
    <span {...props}>{state.checked ? <CheckedIcon /> : <UncheckedIcon />}</span>
  )}
/>
```

Note the two prop-type interfaces: `useRender.ComponentProps` types the
**public** surface (including `render` itself); `useRender.ElementProps`
types only the internal HTML attributes passed to the default element. Never
let the internal type leak into the public one — that's how an internal
implementation detail becomes an accidental public contract.

`type="button"` is only valid on a real `<button>`. Since the component
cannot know what `render` will produce before hydration, an explicit signal
(`nativeButton`) is required rather than inferred.

## 4. Controlled / uncontrolled state

Make the invalid combination a type error, not a runtime footgun:

```tsx
type Controlled<T> = {
  value: T;
  onValueChange: (value: T) => void;
  defaultValue?: never;
};

type Uncontrolled<T> = {
  defaultValue?: T;
  value?: never;
  onValueChange?: never;
};

type ToggleProps<T> = { children: React.ReactNode } & (Controlled<T> | Uncontrolled<T>);
```

```tsx
// ✅ compiles
<Toggle<boolean> value={isOn} onValueChange={setIsOn}>Auto-apply</Toggle>
<Toggle<boolean> defaultValue={false}>Auto-apply</Toggle>

// ❌ type error — both controlled and uncontrolled fields present
<Toggle<boolean> value={isOn} defaultValue={false} />
```

Implementation needs a single hook resolving which mode is active and
warning in dev on a mode switch mid-lifecycle:

```tsx
function useControllableValue<T>({
  value,
  defaultValue,
  onValueChange,
}: {
  value?: T;
  defaultValue: T;
  onValueChange?: (value: T) => void;
}): [T, (value: T) => void] {
  const isControlled = value !== undefined;
  const [internal, setInternal] = useState(defaultValue);

  if (process.env.NODE_ENV !== "production") {
    const wasControlledRef = useRef(isControlled);
    if (wasControlledRef.current !== isControlled) {
      console.error("A component is switching between controlled and uncontrolled — this is not supported.");
    }
    wasControlledRef.current = isControlled;
  }

  const current = isControlled ? (value as T) : internal;
  const set = (next: T) => {
    if (!isControlled) setInternal(next);
    onValueChange?.(next);
  };

  return [current, set];
}
```

Before shipping this: check whether `@base-ui/utils`'s `ReactStore` exposes
a public `useControlled` equivalent in the installed version. If it does,
reuse it instead of maintaining a parallel implementation of the same
problem against a dependency this project already has.

## 5. `children` vs render props

```tsx
// ❌ callback soup — every new slot is another optional function prop
function Composer({ renderHeader, renderFooter, renderActions }: {
  renderHeader?: () => React.ReactNode;
  renderFooter?: () => React.ReactNode;
  renderActions?: () => React.ReactNode;
}) {
  return (
    <form>
      {renderHeader?.()}
      <Input />
      {renderFooter ? renderFooter() : <DefaultFooter />}
      {renderActions?.()}
    </form>
  );
}
```

```tsx
// ✅ children compose naturally, no callback signature to remember
<Composer.Frame>
  <CustomHeader />
  <Composer.Input />
  <Composer.Footer>
    <Composer.Formatting />
    <SubmitButton />
  </Composer.Footer>
</Composer.Frame>
```

Render props stay legitimate when the parent must hand data back to the
caller — a list needs to tell the caller which item and index it's on:

```tsx
<List data={items} renderItem={({ item, index }) => <Item item={item} index={index} />} />
```

## 6. TypeScript prop shapes

Exhaustiveness on a variant discriminant — a forgotten case becomes a
compile error instead of a silent no-render:

```tsx
type Tone = "neutral" | "info" | "attention" | "success" | "danger";

function toneToRole(tone: Tone): string {
  switch (tone) {
    case "neutral":
      return "text-muted";
    case "info":
      return "text-info";
    case "attention":
      return "text-attention";
    case "success":
      return "text-success";
    case "danger":
      return "text-danger";
    default: {
      const exhaustive: never = tone;
      throw new Error(`Unhandled tone: ${exhaustive}`);
    }
  }
}
```

`exactOptionalPropertyTypes` forbids assigning `undefined` to an optional
prop — use conditional spreading instead of a ternary that produces
`undefined`:

```tsx
// ❌ assigns `undefined` explicitly
<Field description={hasHint ? hint : undefined} />

// ✅ the key is absent entirely when there's no hint
<Field {...(hasHint ? { description: hint } : {})} />
```

## 7. The volume/frequency threshold — when *not* to reach for Base UI

SKILL.md §6 says to reuse Base UI for anything with real interaction
machinery. That rule has a threshold, not an absolute: hand-rolling is
legitimate when a pattern shows up once, for one field, in one form — not
when it's the tenth `Menu`/`Combobox`/`Dialog` in a shared component library.
A sibling project (same author, no headless library at all — every
interactive primitive is hand-rolled) makes the boundary concrete with two
real, tested examples:

- **A tag-input combobox** implementing the full ARIA 1.2 "combobox with
  listbox" pattern by hand: `role="combobox"` plus
  `aria-expanded`/`aria-controls`/`aria-activedescendant` on the `<input>`,
  `role="listbox"`/`role="option"` on the suggestion list, full
  Arrow/Enter/Escape/Backspace keyboard handling, and the standard
  `onMouseDown` + `preventDefault` fix so clicking an option doesn't lose
  focus to the input's `onBlur` first. Justified explicitly, in the
  component's own README, as "one field, one form — not the volume/frequency
  that would justify a dependency," and backed by tests covering the full
  interaction path (keyboard selection, mouse selection, dedup, chip removal
  via button and via Backspace) rather than a smoke render.
- **A slide-in drawer** portaled to `document.body`, with `inert` (not just
  `aria-hidden`) kept in sync with open state so a visually-hidden panel also
  drops out of tab order, scroll lock that restores the *previous* `overflow`
  value rather than hardcoding `""`, and Escape-to-close — written after a
  real production bug where nesting the drawer inside a `backdrop-blur`
  header collapsed its `position: fixed` layout down to the header's own row,
  because `backdrop-filter` on an ancestor creates a new containing block for
  `position: fixed` descendants. That exact CSS trap is already listed in
  this repo's own methodology rule, independently of this Skill.

The difference between "hand-roll it" there and "use Base UI" here is not a
difference in principle — it is the same test from `COMPONENTS.md` (how much
reuse, across how much surface) landing on different scale inputs: one field
versus dozens of `Menu`/`Combobox`/`Dialog` instances across three apps. At
that one-field scale, a dependency doesn't pay for itself and hand-rolling
with full rigor is the right call. At Tallyvane's scale, redoing the ARIA
combobox pattern by hand for every instance (company, contact, tag, ...)
means re-solving the same subtle keyboard/focus edge cases every time — which
is exactly the repeated-reimplementation risk Base UI exists to remove here.

If a genuinely one-off, low-traffic control ever needs behavior Base UI
doesn't cover, this is the legitimate escape hatch — hand-roll it with the
same rigor as the examples above (the full keyboard path, dedicated tests, an
honest README section on why), not a shortcut to skip the accessibility work.
