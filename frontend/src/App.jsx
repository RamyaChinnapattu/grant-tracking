import { BrowserRouter, Navigate, Route, Routes } from "react-router-dom";
import { dashboardHomePaths } from "./components/dashboardNavigation";
import BeneficiaryManagement from "./pages/BeneficiaryManagement";
import BeneficiaryEligibility from "./pages/BeneficiaryEligibility";
import DashboardHome from "./pages/DashboardHome";
import AdminDashboardHome from "./pages/AdminDashboardHome";
import AdminReports from "./pages/AdminReports";
import AdminAuditLogs from "./pages/AdminAuditLogs";
import DashboardLayout from "./components/DashboardLayout";
import AdminDashboard from "./pages/AdminDashboard";
import GrantApplicationManagement from "./pages/GrantApplicationManagement";
import Login from "./pages/Login";
import Register from "./pages/Register";
import LandingPage from "./pages/LandingPage";
import PublicInfoPage from "./pages/PublicInfoPage";
import PublicSchemesPage from "./pages/PublicSchemesPage";
import PublicSiteLayout from "./components/PublicSiteLayout";
import SchemeManagement from "./pages/SchemeManagement";
import BeneficiarySchemes from "./pages/BeneficiarySchemes";
import BeneficiaryNotifications from "./pages/BeneficiaryNotifications";
import DashboardPlaceholder from "./pages/DashboardPlaceholder";
import ProtectedRoute from "./components/ProtectedRoute";
import VerificationWorkflow from "./pages/VerificationWorkflow";
import WorkflowSettings from "./pages/WorkflowSettings";

// Unified Consolidated Workspaces
import AdminGrantManagement from "./pages/AdminGrantManagement";
import OfficerReviewVerification from "./pages/OfficerReviewVerification";
import BeneficiaryMyGrant from "./pages/BeneficiaryMyGrant";

function getAuthenticatedHome() {
  const token = localStorage.getItem("token");
  const role = localStorage.getItem("role");
  return token ? dashboardHomePaths[role] : null;
}

function LandingRoute() {
  const home = getAuthenticatedHome();
  return home ? <Navigate to={home} replace /> : <LandingPage />;
}

function PublicAuthRoute({ children }) {
  const home = getAuthenticatedHome();
  return home ? <Navigate to={home} replace /> : children;
}

function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route element={<PublicSiteLayout />}>
          <Route path="/" element={<LandingRoute />} />
          <Route path="/schemes" element={<PublicSchemesPage />} />
          <Route path="/privacy" element={<PublicInfoPage />} />
          <Route path="/terms" element={<PublicInfoPage />} />
        </Route>
        <Route
          path="/login"
          element={
            <PublicAuthRoute>
              <Login />
            </PublicAuthRoute>
          }
        />
        <Route
          path="/register"
          element={
            <PublicAuthRoute>
              <Register />
            </PublicAuthRoute>
          }
        />

        <Route element={<ProtectedRoute allowedRoles={["ADMIN"]} />}>
          <Route element={<DashboardLayout role="ADMIN" />}>
            <Route
              path="/admin-dashboard"
              element={<DashboardHome role="ADMIN" />}
              handle={{ pageTitle: "Dashboard" }}
            />
            <Route
              path="/admin/schemes"
              element={<SchemeManagement />}
              handle={{ pageTitle: "Scheme Management" }}
            />
            <Route
              path="/admin/beneficiaries"
              element={<AdminDashboard />}
              handle={{ pageTitle: "Beneficiary & Officer Management" }}
            />
            <Route
              path="/admin/applications"
              element={<GrantApplicationManagement />}
              handle={{ pageTitle: "Applications & Verification" }}
            />
            <Route
              path="/admin/eligibility"
              element={<Navigate to="/admin/schemes" replace />}
            />
            <Route
              path="/admin/verification"
              element={<VerificationWorkflow />}
              handle={{ pageTitle: "Verification & Approvals" }}
            />
            <Route
              path="/admin/workflow-settings"
              element={<WorkflowSettings />}
              handle={{ pageTitle: "Workflow Settings" }}
            />

            {/* Consolidated Admin Grant Management Workspace */}
            <Route
              path="/admin/grant-management"
              element={<AdminGrantManagement />}
              handle={{ pageTitle: "Grant Management" }}
            />

            {/* Backward-compatibility redirects for fragmented legacy admin routes */}
            <Route
              path="/admin/disbursement"
              element={<Navigate to="/admin/grant-management?tab=plans" replace />}
            />
            <Route
              path="/admin/compliance"
              element={<Navigate to="/admin/grant-management?tab=compliance" replace />}
            />
            <Route
              path="/admin/fund-analytics"
              element={<Navigate to="/admin/grant-management?tab=overview" replace />}
            />
            <Route
              path="/admin/disbursement-dashboard"
              element={<Navigate to="/admin/grant-management?tab=overview" replace />}
            />
            <Route
              path="/admin/utilization-review"
              element={<Navigate to="/admin/grant-management?tab=compliance" replace />}
            />
            <Route
              path="/admin/reports"
              element={<Navigate to="/admin/grant-management?tab=reports" replace />}
            />

            <Route
              path="/admin/audit-logs"
              element={<AdminAuditLogs />}
              handle={{ pageTitle: "Audit Logs" }}
            />
            <Route
              path="/admin/profile"
              element={<DashboardPlaceholder title="Profile" />}
              handle={{ pageTitle: "Profile" }}
            />
          </Route>
        </Route>

        <Route element={<ProtectedRoute allowedRoles={["BENEFICIARY"]} />}>
          <Route element={<DashboardLayout role="BENEFICIARY" />}>
            <Route
              path="/beneficiary-dashboard"
              element={<DashboardHome role="BENEFICIARY" />}
              handle={{ pageTitle: "Dashboard" }}
            />
            <Route
              path="/beneficiary/schemes"
              element={<BeneficiarySchemes />}
              handle={{ pageTitle: "Browse Schemes" }}
            />
            <Route
              path="/beneficiary/applications"
              element={<GrantApplicationManagement />}
              handle={{ pageTitle: "My Applications" }}
            />
            <Route
              path="/beneficiary/apply"
              element={<GrantApplicationManagement />}
              handle={{ pageTitle: "Apply for Grant" }}
            />

            {/* Consolidated Beneficiary My Grant Workspace */}
            <Route
              path="/beneficiary/my-grant"
              element={<BeneficiaryMyGrant />}
              handle={{ pageTitle: "My Grant" }}
            />

            {/* Backward-compatibility redirects for fragmented legacy beneficiary routes */}
            <Route
              path="/beneficiary/disbursements"
              element={<Navigate to="/beneficiary/my-grant?tab=schedule" replace />}
            />
            <Route
              path="/beneficiary/compliance"
              element={<Navigate to="/beneficiary/my-grant?tab=documents" replace />}
            />
            <Route
              path="/beneficiary/utilization"
              element={<Navigate to="/beneficiary/my-grant?tab=expenditure" replace />}
            />

            <Route
              path="/beneficiary/eligibility"
              element={<BeneficiaryEligibility />}
              handle={{ pageTitle: "Eligibility Checker" }}
            />
            <Route
              path="/beneficiary/profile"
              element={<BeneficiaryManagement />}
              handle={{ pageTitle: "My Profile" }}
            />
            <Route
              path="/beneficiary/notifications"
              element={<BeneficiaryNotifications />}
              handle={{ pageTitle: "Notifications" }}
            />
          </Route>
        </Route>

        <Route element={<ProtectedRoute allowedRoles={["OFFICER"]} />}>
          <Route element={<DashboardLayout role="OFFICER" />}>
            <Route
              path="/officer-dashboard"
              element={<DashboardHome role="OFFICER" />}
              handle={{ pageTitle: "Dashboard" }}
            />
            <Route
              path="/officer/applications"
              element={<VerificationWorkflow />}
              handle={{ pageTitle: "My Applications" }}
            />

            {/* Consolidated Officer Review & Verification Workspace */}
            <Route
              path="/officer/review-verification"
              element={<OfficerReviewVerification />}
              handle={{ pageTitle: "Review & Verification" }}
            />

            {/* Backward-compatibility redirects for fragmented legacy officer routes */}
            <Route
              path="/officer/compliance"
              element={<Navigate to="/officer/review-verification?tab=compliance" replace />}
            />
            <Route
              path="/officer/utilization"
              element={<Navigate to="/officer/review-verification?tab=expenditure" replace />}
            />

            <Route
              path="/officer/history"
              element={<VerificationWorkflow historyOnly />}
              handle={{ pageTitle: "Verification History" }}
            />
            <Route
              path="/officer/profile"
              element={<DashboardPlaceholder title="Profile" />}
              handle={{ pageTitle: "Profile" }}
            />
            <Route
              path="/officer/notifications"
              element={<BeneficiaryNotifications />}
              handle={{ pageTitle: "Notifications" }}
            />

            {/* Backward compatibility redirects for legacy duplicate routes */}
            <Route
              path="/officer/assigned"
              element={<Navigate to="/officer/applications" replace />}
            />
            <Route
              path="/officer/pending-verification"
              element={<Navigate to="/officer/applications" replace />}
            />
            <Route
              path="/officer/review"
              element={<Navigate to="/officer/applications" replace />}
            />
          </Route>
        </Route>

        <Route path="*" element={<Navigate to="/login" replace />} />
      </Routes>
    </BrowserRouter>
  );
}

export default App;
