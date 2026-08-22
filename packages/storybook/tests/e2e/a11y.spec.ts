import { defineA11ySpecs } from "test-kit/specs/a11y";
import { readStoryManifest } from "./story-manifest";

defineA11ySpecs(readStoryManifest(), { surface: "component" });
