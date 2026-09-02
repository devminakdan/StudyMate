import { createTheme } from '@mui/material/styles';

export const appTheme = createTheme({
  palette: {
    background: { default: '#f8f6ef', paper: '#fffefb' },
    primary: { main: '#008487', dark: '#006e72' },
    text: { primary: '#1d2a39', secondary: '#65707c' },
  },
  shape: { borderRadius: 10 },
  typography: {
    fontFamily: "'Libre Franklin', Arial, sans-serif",
    h1: { fontFamily: "'Source Serif 4', Georgia, serif" },
    h2: { fontFamily: "'Source Serif 4', Georgia, serif" },
  },
  components: {
    MuiCssBaseline: {
      styleOverrides: {
        body: { minWidth: 320 },
        '*': { boxSizing: 'border-box' },
      },
    },
    MuiButton: {
      defaultProps: { disableElevation: true },
      styleOverrides: {
        root: { borderRadius: 10, fontSize: '15px', fontWeight: 600, minHeight: 46, padding: '12px 16px', textTransform: 'none' },
      },
    },
    MuiOutlinedInput: {
      styleOverrides: {
        root: { backgroundColor: '#fff', borderRadius: 10 },
        notchedOutline: { borderColor: '#deded9' },
      },
    },
    MuiInputBase: {
      styleOverrides: { input: { fontSize: '15px', padding: '12px 14px' } },
    },
    MuiFormLabel: {
      styleOverrides: { root: { color: '#1d2a39', fontSize: '13px', fontWeight: 600 } },
    },
    MuiLink: {
      styleOverrides: { root: { color: '#008487', fontWeight: 600 } },
    },
  },
});
