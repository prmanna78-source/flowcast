import React from "react";
import ReactDOM from "react-dom/client";
import { Scene } from "./Scene";

function initTopDock() {
  console.log("[AnimatedTopDock] Initializing ThreeUI AnimatedTopDock...");
  const container = document.getElementById("animated-top-dock-root");
  if (!container) {
    console.error("[AnimatedTopDock] #animated-top-dock-root not found!");
    return;
  }

  const root = ReactDOM.createRoot(container);
  root.render(<Scene />);
  console.log("[AnimatedTopDock] Scene mounted successfully.");

  // Interactive bridge for FlowCast navigation while keeping component source exact
  container.addEventListener("click", (e) => {
    const target = e.target as HTMLElement | null;
    if (!target) return;

    // Check if clicked inside a dock item
    const dockBtn = target.closest<HTMLButtonElement>("[data-dock-item]");
    if (dockBtn) {
      const text = dockBtn.textContent?.trim().toLowerCase() || "";
      if (text.includes("product")) {
        document.getElementById("commute-planner-section")?.scrollIntoView({ behavior: "smooth" });
      } else if (text.includes("solutions")) {
        document.getElementById("saved-commutes-section")?.scrollIntoView({ behavior: "smooth" });
      } else if (text.includes("docs")) {
        document.getElementById("map-section")?.scrollIntoView({ behavior: "smooth" });
      } else if (text.includes("pricing")) {
        document.getElementById("hazards-section")?.scrollIntoView({ behavior: "smooth" });
      } else if (text.includes("changelog")) {
        document.getElementById("sources-section")?.scrollIntoView({ behavior: "smooth" });
      }
    }

    // Check if clicked CTA button "Start building"
    const ctaBtn = target.closest<HTMLButtonElement>(".atd-modern__cta");
    if (ctaBtn) {
      document.getElementById("commute-planner-section")?.scrollIntoView({ behavior: "smooth" });
    }

    // Check if clicked "Sign in" ghost button
    const ghostBtn = target.closest<HTMLButtonElement>(".atd-modern__ghost");
    if (ghostBtn) {
      const alertBtn = document.getElementById("alert-permission-btn");
      if (alertBtn) alertBtn.click();
    }
  });
}

if (document.readyState === "loading") {
  document.addEventListener("DOMContentLoaded", initTopDock);
} else {
  initTopDock();
}
