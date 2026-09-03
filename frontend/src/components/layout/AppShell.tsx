import {
  Box,
  Divider,
  IconButton,
  LinearProgress,
  List,
  ListItemButton,
  Stack,
  SvgIcon,
  Tooltip,
  Typography,
} from '@mui/material';
import type { ReactNode } from 'react';
import { useState } from 'react';
import { NavLink } from 'react-router-dom';
import { useCurrentUserQuery } from '@/features/auth/api/authQueries';
import { Brand } from '../ui/Brand';

interface AppShellProps { children: ReactNode; }
interface SidebarIconProps { name: 'home' | 'courses' | 'quizzes' | 'profile'; }

const sidebarWidth = { expanded: 264, collapsed: 88 };

function SidebarIcon({ name }: SidebarIconProps) {
  const paths = {
    home: <path d="m3 10 9-7 9 7v10a1 1 0 0 1-1 1H4a1 1 0 0 1-1-1V10Zm6 11v-6h6v6" />,
    courses: <path d="M3.5 6.5A2.5 2.5 0 0 1 6 4h4l2 2h6A2.5 2.5 0 0 1 20.5 8.5v9A2.5 2.5 0 0 1 18 20H6a2.5 2.5 0 0 1-2.5-2.5v-11Z" />,
    quizzes: <path d="M7 3.5h10A3.5 3.5 0 0 1 20.5 7v10a3.5 3.5 0 0 1-3.5 3.5H7A3.5 3.5 0 0 1 3.5 17V7A3.5 3.5 0 0 1 7 3.5Zm2.2 8.4 1.8 1.8 4.2-4.2" />,
    profile: <path d="M12 3.5l1.6 2.1 2.6-.4.7 2.5 2.4 1.1-1 2.4 1 2.4-2.4 1.1-.7 2.5-2.6-.4L12 20.5l-1.6-2.1-2.6.4-.7-2.5-2.4-1.1 1-2.4-1-2.4 2.4-1.1.7-2.5 2.6.4L12 3.5Zm0 5.4a3.1 3.1 0 1 0 0 6.2 3.1 3.1 0 0 0 0-6.2Z" />,
  } as const;
  return <SvgIcon sx={{ fill: 'none', fontSize: 29, stroke: 'currentColor', strokeLinecap: 'round', strokeLinejoin: 'round', strokeWidth: 1.8 }} viewBox="0 0 24 24">{paths[name]}</SvgIcon>;
}

function SidebarItem({ collapsed, icon, label, to }: { collapsed: boolean; icon: SidebarIconProps['name']; label: string; to?: string }) {
  const content = <>
    <SidebarIcon name={icon} />
    {!collapsed && <Typography sx={{ fontSize: 16, fontWeight: 700, ml: 2.1 }}>{label}</Typography>}
  </>;
  const item = to
    ? <ListItemButton component={NavLink} end sx={{ '&.active': { bgcolor: '#d7f3f1', color: 'primary.main' }, borderRadius: 2, justifyContent: collapsed ? 'center' : 'flex-start', minHeight: 58, px: collapsed ? 0 : 2.25 }} to={to}>{content}</ListItemButton>
    : <ListItemButton disabled sx={{ borderRadius: 2, justifyContent: collapsed ? 'center' : 'flex-start', minHeight: 58, px: collapsed ? 0 : 2.25 }}>{content}</ListItemButton>;
  return collapsed ? <Tooltip placement="right" title={label}>{item}</Tooltip> : item;
}

export function AppShell({ children }: AppShellProps) {
  const [collapsed, setCollapsed] = useState(false);
  const currentUserQuery = useCurrentUserQuery(true);
  const username = currentUserQuery.data?.username ?? 'StudyMate user';
  const initial = username.slice(0, 1).toUpperCase();
  const width = collapsed ? sidebarWidth.collapsed : sidebarWidth.expanded;

  return <Box sx={{ bgcolor: 'background.default', display: 'flex', minHeight: '100vh' }}>
    <Box component="aside" sx={{ bgcolor: '#fffefb', borderRight: '1px solid #e7e3db', flex: `0 0 ${width}px`, minHeight: '100vh', position: 'sticky', top: 0, transition: 'flex-basis 240ms ease', zIndex: 2 }}>
      <Stack sx={{ height: '100%', overflow: 'visible', p: collapsed ? 2 : 3.5 }}>
        <Box sx={{ '& .brand__name': { fontSize: 20 }, alignItems: 'center', display: 'flex', height: 58, justifyContent: collapsed ? 'center' : 'flex-start', overflow: 'hidden' }}>
          {collapsed ? <Box aria-label="StudyMate" sx={{ bgcolor: 'primary.main', borderRadius: '50%', height: 18, width: 18 }} /> : <Brand />}
        </Box>

        <IconButton aria-label={collapsed ? 'Expand sidebar' : 'Collapse sidebar'} onClick={() => setCollapsed((value) => !value)} sx={{ '&:hover': { bgcolor: '#fff' }, bgcolor: '#fff', border: '1px solid #e2ded5', boxShadow: '0 3px 10px rgba(29,42,57,.14)', height: 48, position: 'absolute', right: -24, top: 40, width: 48, zIndex: 3 }}>
          <Typography aria-hidden="true" sx={{ color: 'text.secondary', fontSize: 30, lineHeight: 1 }}>{collapsed ? '›' : '‹'}</Typography>
        </IconButton>

        <List disablePadding sx={{ display: 'grid', gap: 1.25, mt: 5 }}>
          <SidebarItem collapsed={collapsed} icon="home" label="Dashboard" to="/courses" />
          <SidebarItem collapsed={collapsed} icon="courses" label="Courses" />
          <SidebarItem collapsed={collapsed} icon="quizzes" label="Quizzes" />
          <SidebarItem collapsed={collapsed} icon="profile" label="Profile" />
        </List>

        <Box sx={{ mt: 'auto', overflow: 'hidden' }}>
          <Divider sx={{ mb: 3 }} />
          <Stack spacing={1.2} sx={{ alignItems: 'center' }}>
            <Box sx={{ alignItems: 'center', bgcolor: 'primary.main', borderRadius: '50%', color: '#fff', display: 'flex', fontFamily: "'Source Serif 4', Georgia, serif", fontSize: collapsed ? 26 : 34, fontWeight: 600, height: collapsed ? 60 : 92, justifyContent: 'center', transition: 'height 240ms ease, width 240ms ease', width: collapsed ? 60 : 92 }}>{initial}</Box>
            {!collapsed && <><Typography sx={{ fontWeight: 700 }}>{username}</Typography><Typography color="text.secondary" variant="body2">Free plan</Typography><Box sx={{ px: 2, pt: .5, width: '100%' }}><LinearProgress aria-label="Storage used" sx={{ borderRadius: 99, height: 7 }} value={0} variant="determinate" /></Box></>}
          </Stack>
        </Box>
      </Stack>
    </Box>
    <Box sx={{ flex: 1, minWidth: 0 }}>{children}</Box>
  </Box>;
}
