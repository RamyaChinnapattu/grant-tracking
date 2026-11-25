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
    { label: "Dashboard", path: "/admin-dashboard", icon: LayoutDashboard, section: "Overview" },
    { label: "Scheme Management", path: "/admin/schemes", icon: FolderKanban, section: "Programs & Users" },
    {
      label: "Beneficiary & Officer Management",
      path: "/admin/beneficiaries",
      icon: UsersRound,
      section: "Programs & Users",
    },
    {
      label: "Applications & Verification",
      path: "/admin/applications",
      icon: ClipboardList,
      section: "Review & Finance",
    },
    {
      label: "Grant Management",
      path: "/admin/grant-management",
      icon: CircleDollarSign,
      section: "Review & Finance",
    },
    { label: "Audit Logs", path: "/admin/audit-logs", icon: FileClock, section: "Governance" },
    { label: "Workflow Settings", path: "/admin/workflow-settings", icon: Settings2, section: "Governance" },
  ],
  BENEFICIARY: [
    {
      label: "Dashboard",
      path: "/beneficiary-dashboard",
      icon: LayoutDashboard,
      section: "Overview",
    },
    { label: "Browse Schemes", path: "/beneficiary/schemes", icon: FolderKanban, section: "Applications" },
    {
      label: "My Applications",
      path: "/beneficiary/applications",
      icon: ClipboardList,
      section: "Applications",
    },
    {
      label: "Apply for Grant",
      path: "/beneficiary/apply",
      icon: FilePlus2,
      section: "Applications",
    },
    { label: "My Grant", path: "/beneficiary/my-grant", icon: CircleDollarSign, section: "Grants & Account" },
    { label: "My Profile", path: "/beneficiary/profile", icon: UserRound, section: "Grants & Account" },
    {
      label: "Notifications",
      path: "/beneficiary/notifications",
      icon: Bell,
      section: "Grants & Account",
    },
  ],
  OFFICER: [
    { label: "Dashboard", path: "/officer-dashboard", icon: LayoutDashboard, section: "Overview" },
    {
      label: "My Applications",
      path: "/officer/applications",
      icon: ClipboardList,
      section: "Work Queues",
    },
    {
      label: "Review & Verification",
      path: "/officer/review-verification",
      icon: BadgeCheck,
      section: "Work Queues",
    },
    {
      label: "Verification History",
      path: "/officer/history",
      icon: FileClock,
      section: "Work Queues",
    },
    { label: "Notifications", path: "/officer/notifications", icon: Bell, section: "Account" },
    { label: "Profile", path: "/officer/profile", icon: UserRound, section: "Account" },
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
