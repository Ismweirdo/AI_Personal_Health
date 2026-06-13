/** @type {import('tailwindcss').Config} */

export default {
  darkMode: "class",
  content: ["./index.html", "./src/**/*.{js,ts,vue}"],
  theme: {
    container: {
      center: true,
    },
    extend: {
      colors: {
        ink: {
          paper: '#F7F3E8',
          mist: '#ECE5D7',
          wash: '#D9D0C0',
          stone: '#3E3A35',
          muted: '#6D675E',
          pine: '#3B6B57',
          'pine-dark': '#28513F',
          cinnabar: '#A34A3A',
          gold: '#B08A3C',
        }
      }
    },
  },
  plugins: [],
};
