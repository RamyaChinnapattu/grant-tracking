import {
  BadgeCheck,
  Bell,
  CircleDollarSign,
  ClipboardList,
  FileClock,
  FilePlus2,
  FolderKanban,
  LayoutDashboard,
  Settings2,
  UserRound,
  UsersRound,
} from "lucide-react";

export const dashboardNavigation = {
  ADMIN: [
    { label: "Dashboard", path: "/admin-dashboard", icon: LayoutDashboard },
    { label: "Scheme Management", path: "/admin/schemes", icon: FolderKanban },
    {
      label: "Beneficiary & Officer Management",
      path: "/admin/beneficiaries",
      icon: UsersRound,
    },
    {
      label: "Applications & Verification",
      path: "/admin/applications",
      icon: ClipboardList,
    },
    {
      label: "Grant Management",
      path: "/admin/grant-management",
      icon: CircleDollarSign,
    },
    { label: "Audit Logs", path: "/admin/audit-logs", icon: FileClock },
    { label: "Workflow Settings", path: "/admin/workflow-settings", icon: Settings2 },
  ],
  BENEFICIARY: [
    {
      label: "Dashboard",
      path: "/beneficiary-dashboard",
      icon: LayoutDashboard,
    },
    { label: "Browse Schemes", path: "/beneficiary/schemes", icon: FolderKanban },
    {
      label: "My Applications",
      path: "/beneficiary/applications",
      icon: ClipboardList,
    },
    {
      label: "Apply for Grant",
      path: "/beneficiary/apply",
      icon: FilePlus2,
    },
    { label: "My Grant", path: "/beneficiary/my-grant", icon: CircleDollarSign },
    { label: "My Profile", path: "/beneficiary/profile", icon: UserRound },
    {
      label: "Notifications",
      path: "/beneficiary/notifications",
      icon: Bell,
    },
  ],
  OFFICER: [
    { label: "Dashboard", path: "/officer-dashboard", icon: LayoutDashboard },
    {
      label: "My Applications",
      path: "/officer/applications",
      icon: ClipboardList,
    },
    {
      label: "Review & Verification",
      path: "/officer/review-verification",
      icon: BadgeCheck,
    },
    {
      label: "Verification History",
      path: "/officer/history",
      icon: FileClock,
    },
    { label: "Notifications", path: "/officer/notifications", icon: Bell },
    { label: "Profile", path: "/officer/profile", icon: UserRound },
  ],
};

export const roleLabels = {
  ADMIN: "Administrator",
  BENEFICIARY: "Beneficiary",
  OFFICER: "Officer",
};

export const dashboardHomePaths = {
  ADMIN: "/admin-dashboard",
  BENEFICIARY: "/beneficiary-dashboard",
  OFFICER: "/officer-dashboard",
};
