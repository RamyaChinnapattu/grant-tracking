import {
  BarChart3,
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
      label: "Grant Disbursement & Compliance",
      path: "/admin/disbursement",
      icon: CircleDollarSign,
    },
    { label: "Compliance Requirements", path: "/admin/compliance", icon: BadgeCheck },
    { label: "Fund Analytics", path: "/admin/fund-analytics", icon: BarChart3 },
    { label: "Disbursement Dashboard", path: "/admin/disbursement-dashboard", icon: CircleDollarSign },
    { label: "Utilization Review", path: "/admin/utilization-review", icon: BadgeCheck },
    {
      label: "Reports & Analytics",
      path: "/admin/reports",
      icon: BarChart3,
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
    { label: "My Disbursement Schedule", path: "/beneficiary/disbursements", icon: CircleDollarSign },
    { label: "My Compliance", path: "/beneficiary/compliance", icon: BadgeCheck },
    { label: "My Grant Expenditure", path: "/beneficiary/utilization", icon: CircleDollarSign },
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
      label: "Verification History",
      path: "/officer/history",
      icon: FileClock,
    },
    { label: "Compliance Review", path: "/officer/compliance", icon: BadgeCheck },
    { label: "Utilization Review", path: "/officer/utilization", icon: CircleDollarSign },
    { label: "Profile", path: "/officer/profile", icon: UserRound },
    { label: "Notifications", path: "/officer/notifications", icon: Bell },
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
