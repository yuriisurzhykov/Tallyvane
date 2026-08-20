import { defineWcagContrastSpecs } from "test-kit/specs/contrast-wcag";
import { readStoryManifest } from "./story-manifest";

defineWcagContrastSpecs(readStoryManifest());
