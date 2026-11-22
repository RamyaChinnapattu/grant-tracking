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
import DisbursementManagement from "./pages/DisbursementManagement";
import ProtectedRoute from "./components/ProtectedRoute";
import VerificationWorkflow from "./pages/VerificationWorkflow";
import WorkflowSettings from "./pages/WorkflowSettings";
import BeneficiaryDisbursements from "./pages/BeneficiaryDisbursements";
import BeneficiaryCompliance from "./pages/BeneficiaryCompliance";
import ComplianceCenter from "./pages/ComplianceCenter";
import FundAnalytics from "./pages/FundAnalytics";
import BeneficiaryUtilization from "./pages/BeneficiaryUtilization";
import UtilizationReview from "./pages/UtilizationReview";

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
            <Route path="/admin/workflow-settings" element={<WorkflowSettings />} handle={{ pageTitle: "Workflow Settings" }} />
            <Route
              path="/admin/disbursement"
              element={<DisbursementManagement />}
              handle={{ pageTitle: "Grant Disbursement & Compliance" }}
            />
            <Route path="/admin/compliance" element={<ComplianceCenter />} handle={{ pageTitle: "Compliance Management" }} />
            <Route path="/admin/fund-analytics" element={<FundAnalytics />} handle={{ pageTitle: "Fund Analytics" }} />
            <Route path="/admin/utilization-review" element={<UtilizationReview />} handle={{ pageTitle: "Utilization Review" }} />
            <Route
              path="/admin/reports"
              element={<AdminReports />}
              handle={{ pageTitle: "Reports & Analytics" }}
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
            <Route path="/beneficiary/disbursements" element={<BeneficiaryDisbursements />} handle={{ pageTitle: "My Disbursement Schedule" }} />
            <Route path="/beneficiary/compliance" element={<BeneficiaryCompliance />} handle={{ pageTitle: "My Compliance" }} />
            <Route path="/beneficiary/utilization" element={<BeneficiaryUtilization />} handle={{ pageTitle: "My Grant Expenditure" }} />
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
            <Route path="/officer/compliance" element={<ComplianceCenter />} handle={{ pageTitle: "Compliance Review" }} />
            <Route path="/officer/utilization" element={<UtilizationReview />} handle={{ pageTitle: "Utilization Review" }} />
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
              element={<DashboardPlaceholder title="Notifications" />}
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
