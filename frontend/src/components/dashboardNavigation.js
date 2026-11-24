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
