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
  FIELD_OFFICER: [
    { label: "Dashboard", path: "/field-officer-dashboard", icon: LayoutDashboard },
    { label: "Field Verification Queue", path: "/field-officer/applications", icon: ClipboardList },
    { label: "Verification History", path: "/field-officer/history", icon: FileClock },
    { label: "Compliance Review", path: "/field-officer/compliance", icon: BadgeCheck },
    { label: "Profile", path: "/field-officer/profile", icon: UserRound },
  ],
  DISTRICT_OFFICER: [
    { label: "Dashboard", path: "/district-officer-dashboard", icon: LayoutDashboard },
    { label: "District Review Queue", path: "/district-officer/applications", icon: ClipboardList },
    { label: "Verification History", path: "/district-officer/history", icon: FileClock },
    { label: "Compliance Review", path: "/district-officer/compliance", icon: BadgeCheck },
    { label: "Profile", path: "/district-officer/profile", icon: UserRound },
  ],
  FINANCE_OFFICER: [
    { label: "Dashboard", path: "/finance-officer-dashboard", icon: LayoutDashboard },
    { label: "Financial Approval Queue", path: "/finance-officer/applications", icon: ClipboardList },
    { label: "Verification History", path: "/finance-officer/history", icon: FileClock },
    { label: "Scheme Allocations & Budgets", path: "/finance-officer/schemes", icon: FolderKanban },
    { label: "Compliance Review", path: "/finance-officer/compliance", icon: BadgeCheck },
    { label: "Profile", path: "/finance-officer/profile", icon: UserRound },
  ],
};

export const roleLabels = {
  ADMIN: "Administrator",
  BENEFICIARY: "Beneficiary",
  OFFICER: "Officer",
  FIELD_OFFICER: "Field Officer",
  DISTRICT_OFFICER: "District Officer",
  FINANCE_OFFICER: "Finance Officer",
};

export const dashboardHomePaths = {
  ADMIN: "/admin-dashboard",
  BENEFICIARY: "/beneficiary-dashboard",
  FIELD_OFFICER: "/field-officer-dashboard",
  DISTRICT_OFFICER: "/district-officer-dashboard",
  FINANCE_OFFICER: "/finance-officer-dashboard",
  OFFICER: "/officer-dashboard",
};
