import type { Config } from "tailwindcss";

const config: Config = {
  content: ["./src/**/*.{js,ts,jsx,tsx,mdx}"],
  theme: {
    extend: {
      colors: {
        midnight: "#0D0722",
        violet: "#A47BFF",
        gold: "#FFD98A",
      },
    },
  },
  plugins: [],
};

export default config;
