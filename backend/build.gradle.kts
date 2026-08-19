// Root build file — deliberately almost empty, and it stays that way.
//
// There is no `allprojects { }` or `subprojects { }` block here on purpose.
// Configuration injected from the root is invisible from the module you are
// reading: you open a module's build file, see three lines, and have no way to
// tell what else is being applied to it. Convention plugins in build-logic do
// the same job while staying greppable — the module names the plugin it uses,
// and the plugin is a real file you can open.
