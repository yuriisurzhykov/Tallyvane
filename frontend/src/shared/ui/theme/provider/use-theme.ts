"use client";

import * as React from "react";
import { ThemeContext } from "./theme.context";
import type { ThemeContextValue } from "./theme.types";

export function useTheme(): ThemeContextValue {
    const context = React.useContext(ThemeContext);
    if (!context) throw new Error("useTheme must be used within <ThemeProvider />");
    return context;
}
