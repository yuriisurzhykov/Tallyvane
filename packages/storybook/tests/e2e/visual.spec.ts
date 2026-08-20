import { defineVisualSpecs } from "test-kit/specs/visual";
import { readStoryManifest } from "./story-manifest";

defineVisualSpecs(readStoryManifest());
