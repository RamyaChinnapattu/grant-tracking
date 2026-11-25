import { useEffect, useRef, useState } from "react";
import {
  Bell,
  ChevronDown,
  ChevronLeft,
  Menu,
  UserRound,
  X,
} from "lucide-react";
import {
  Link,
  NavLink,
  Outlet,
  useLocation,
} from "react-router-dom";
import LogoutButton from "./LogoutButton";
import {
  dashboardNavigation,
  roleLabels,
} from "./dashboardNavigation";
import "../styles/DashboardLayout.css";
import api from "../services/api";

function DashboardLayout({ role }) {
  const [sidebarCollapsed, setSidebarCollapsed] = useState(false);
  const [mobileSidebarOpen, setMobileSidebarOpen] = useState(false);
  const [notificationsOpen, setNotificationsOpen] = useState(false);
  const [profileOpen, setProfileOpen] = useState(false);
  const [notificationData, setNotificationData] = useState({
    unreadCount: 0,
    items: [],
  });

  const notifRef = useRef(null);
  const profileRef = useRef(null);

  useEffect(() => {
    api
      .get("/workflow/notifications")
      .then(({ data }) => setNotificationData(data || { unreadCount: 0, items: [] }))
      .catch(() => {});
  }, []);

  useEffect(() => {
    const handleClickOutside = (e) => {
      if (notifRef.current && !notifRef.current.contains(e.target)) {
        setNotificationsOpen(false);
      }
      if (profileRef.current && !profileRef.current.contains(e.target)) {
        setProfileOpen(false);
      }
    };
    const handleKeyDown = (e) => {
      if (e.key === "Escape") {
        setNotificationsOpen(false);
        setProfileOpen(false);
        setMobileSidebarOpen(false);
      }
    };
    document.addEventListener("mousedown", handleClickOutside);
    document.addEventListener("keydown", handleKeyDown);
    return () => {
      document.removeEventListener("mousedown", handleClickOutside);
      document.removeEventListener("keydown", handleKeyDown);
    };
  }, []);

  const location = useLocation();
  const navigation = dashboardNavigation[role] || [];
  const currentPage = navigation.find(
    (item) => item.path === location.pathname
  );
  const title =
    currentPage?.label ||
    (location.pathname.endsWith("/profile") ? "Profile" : "Dashboard");
  const fullName = localStorage.getItem("fullName") || roleLabels[role];
  const email = localStorage.getItem("email") || "";
  const officerLevel = localStorage.getItem("officerLevel") || "";
  const jurisdictionDistrict = localStorage.getItem("jurisdictionDistrict") || "";
  const jurisdictionState = localStorage.getItem("jurisdictionState") || "";
  const initials = fullName
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((part) => part[0].toUpperCase())
    .join("");
  const notificationPath = navigation.find(
    (item) => item.label === "Notifications"
  )?.path;
  const profilePath = `/${role.toLowerCase()}/profile`;

  const closeMobileSidebar = () => setMobileSidebarOpen(false);

  // Group navigation items by section preserving original order
  const navGroups = navigation.reduce((groups, item) => {
    const sec = item.section || "Workspace";
    const last = groups[groups.length - 1];
    if (last && last.section === sec) {
      last.items.push(item);
    } else {
      groups.push({ section: sec, items: [item] });
    }
    return groups;
  }, []);

  const handleMarkNotificationRead = (itemId) => {
    api
      .post(`/workflow/notifications/${itemId}/read`)
      .then(() =>
        api
          .get("/workflow/notifications")
          .then(({ data }) =>
            setNotificationData(data || { unreadCount: 0, items: [] })
          )
      )
      .catch(() => {});
  };

  return (
    <div
      className={`dashboard-shell${sidebarCollapsed ? " dashboard-shell--collapsed" : ""}`}
    >
      {mobileSidebarOpen && (
        <button
          className="dashboard-shell__scrim"
          type="button"
          aria-label="Close navigation menu"
          onClick={closeMobileSidebar}
        />
      )}

      <aside
        className={`dashboard-sidebar${sidebarCollapsed ? " dashboard-sidebar--collapsed" : ""}${mobileSidebarOpen ? " dashboard-sidebar--open" : ""}`}
        aria-label={`${roleLabels[role]} navigation`}
      >
        <div className="dashboard-sidebar__brand-row">
          <Link
            className="dashboard-sidebar__brand"
            to={dashboardNavigation[role]?.[0]?.path || "/"}
            onClick={closeMobileSidebar}
            aria-label="Grantora dashboard"
            title={sidebarCollapsed ? "Grantora" : undefined}
          >
            <span className="dashboard-sidebar__brand-mark">G</span>
            <span className="dashboard-sidebar__brand-copy">
              <span className="dashboard-sidebar__brand-name">Grantora</span>
              <span className="dashboard-sidebar__brand-tag">Grant Platform</span>
            </span>
          </Link>
          <button
            className="dashboard-sidebar__mobile-close"
            type="button"
            aria-label="Close navigation menu"
            onClick={closeMobileSidebar}
          >
            <X size={20} />
          </button>
        </div>

        <nav className="dashboard-sidebar__nav" aria-label="Main Navigation">
          {navGroups.map((group) => (
            <div key={group.section} className="dashboard-sidebar__group">
              <div className="dashboard-sidebar__section-label">
                {group.section}
              </div>
              {group.items.map(({ label, path, icon: Icon }) => (
                <NavLink
                  key={path}
                  to={path}
                  end
                  title={sidebarCollapsed ? label : undefined}
                  className={({ isActive }) =>
                    `dashboard-sidebar__link${isActive ? " dashboard-sidebar__link--active" : ""}`
                  }
                  onClick={closeMobileSidebar}
                >
                  <span className="dashboard-sidebar__link-icon" aria-hidden="true">
                    {Icon ? <Icon size={19} strokeWidth={1.85} /> : null}
                  </span>
                  <span className="dashboard-sidebar__link-label">{label}</span>
                  <span className="dashboard-sidebar__tooltip" role="tooltip">
                    {label}
                  </span>
                </NavLink>
              ))}
            </div>
          ))}
        </nav>

        <div className="dashboard-sidebar__footer">
          <div
            className="dashboard-sidebar__user"
            title={sidebarCollapsed ? `${fullName} (${roleLabels[role]})` : undefined}
          >
            <span className="dashboard-sidebar__avatar" aria-hidden="true">
              {initials || "G"}
            </span>
            <span className="dashboard-sidebar__user-copy">
              <strong>{fullName}</strong>
              <span>{roleLabels[role]}</span>
            </span>
          </div>
          <LogoutButton
            className="dashboard-sidebar__logout"
            isCollapsed={sidebarCollapsed}
          />
        </div>
      </aside>

      <div className="dashboard-main">
        <header className="dashboard-topbar">
          <div className="dashboard-topbar__heading">
            <button
              className="dashboard-topbar__menu"
              type="button"
              aria-label={
                mobileSidebarOpen ? "Close navigation menu" : "Open navigation menu"
              }
              aria-expanded={mobileSidebarOpen}
              onClick={() => {
                setMobileSidebarOpen((open) => !open);
                setNotificationsOpen(false);
                setProfileOpen(false);
              }}
            >
              <Menu size={20} />
            </button>
            <button
              className="dashboard-topbar__collapse"
              type="button"
              aria-label={
                sidebarCollapsed ? "Expand sidebar" : "Collapse sidebar"
              }
              onClick={() => setSidebarCollapsed((collapsed) => !collapsed)}
            >
              <ChevronLeft
                size={18}
                className={sidebarCollapsed ? "is-reversed" : ""}
              />
            </button>
            <div className="dashboard-topbar__breadcrumb">
              <span className="dashboard-topbar__eyebrow">
                {roleLabels[role]} Portal
              </span>
              <h1>{title}</h1>
            </div>
          </div>

          <div className="dashboard-topbar__actions">
            <div className="dashboard-notifications" ref={notifRef}>
              <button
                className="dashboard-notifications__button"
                type="button"
                aria-label="Notifications"
                aria-expanded={notificationsOpen}
                onClick={() => {
                  setNotificationsOpen((open) => !open);
                  setProfileOpen(false);
                }}
              >
                <Bell size={18} />
                {notificationData.unreadCount > 0 && (
                  <span className="notification-count">
                    {notificationData.unreadCount > 99
                      ? "99+"
                      : notificationData.unreadCount}
                  </span>
                )}
              </button>
              {notificationsOpen && (
                <div
                  className="dashboard-notifications__panel"
                  role="dialog"
                  aria-label="Notifications"
                >
                  <div className="dashboard-notifications__panel-head">
                    <strong>Notifications</strong>
                    {notificationData.unreadCount > 0 && (
                      <span className="saas-badge saas-badge--info">
                        {notificationData.unreadCount} unread
                      </span>
                    )}
                  </div>
                  <div className="dashboard-notifications__list">
                    {notificationData.items.slice(0, 5).map((item) => (
                      <button
                        type="button"
                        className={`notification-item${!item.readAt ? " notification-item--unread" : ""}`}
                        key={item.id}
                        onClick={() => handleMarkNotificationRead(item.id)}
                      >
                        <span className="notification-item__msg">{item.message}</span>
                        {!item.readAt && (
                          <span className="notification-item__dot" aria-label="Unread" />
                        )}
                      </button>
                    ))}
                    {notificationData.items.length === 0 && (
                      <p className="dashboard-notifications__empty">
                        You’re all caught up. No notifications yet.
                      </p>
                    )}
                  </div>
                  {notificationPath && (
                    <div className="dashboard-notifications__panel-foot">
                      <Link
                        to={notificationPath}
                        onClick={() => setNotificationsOpen(false)}
                      >
                        View all notifications
                      </Link>
                    </div>
                  )}
                </div>
              )}
            </div>

            <div className="dashboard-profile-menu" ref={profileRef}>
              <button
                type="button"
                className="dashboard-topbar__profile-btn"
                aria-expanded={profileOpen}
                aria-haspopup="menu"
                onClick={() => {
                  setProfileOpen((open) => !open);
                  setNotificationsOpen(false);
                }}
              >
                <span className="dashboard-topbar__avatar" aria-hidden="true">
                  {initials || "G"}
                </span>
                <span className="dashboard-topbar__profile-copy">
                  <strong>{fullName}</strong>
                  <span>{email || roleLabels[role]}</span>
                </span>
                <ChevronDown
                  size={15}
                  className={`dashboard-topbar__profile-chevron${profileOpen ? " is-open" : ""}`}
                />
              </button>

              {profileOpen && (
                <div
                  className="dashboard-profile-dropdown"
                  role="menu"
                  aria-label="Account menu"
                >
                  <div className="dashboard-profile-dropdown__header">
                    <strong>{fullName}</strong>
                    <span>{email || roleLabels[role]}</span>
                    <div className="dashboard-profile-dropdown__meta">
                      <span className="saas-badge saas-badge--info">
                        {roleLabels[role]}
                        {officerLevel ? ` · ${officerLevel}` : ""}
                      </span>
                      {(jurisdictionDistrict || jurisdictionState) && (
                        <small>
                          {[jurisdictionDistrict, jurisdictionState]
                            .filter(Boolean)
                            .join(", ")}
                        </small>
                      )}
                    </div>
                  </div>
                  <Link
                    to={profilePath}
                    className="dashboard-profile-dropdown__item"
                    role="menuitem"
                    onClick={() => setProfileOpen(false)}
                  >
                    <UserRound size={16} />
                    <span>Account Profile</span>
                  </Link>
                  <div className="dashboard-profile-dropdown__divider" />
                  <LogoutButton className="dashboard-profile-dropdown__logout" />
                </div>
              )}
            </div>
          </div>
        </header>

        <main className="dashboard-content">
          <Outlet />
        </main>
      </div>
    </div>
  );
}

export default DashboardLayout;
