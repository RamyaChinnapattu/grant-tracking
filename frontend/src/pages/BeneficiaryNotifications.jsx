import { useEffect, useState } from "react";
import {
  Bell,
  Check,
  CheckCheck,
  Clock,
  AlertCircle,
  Info,
  RefreshCw,
  ExternalLink,
} from "lucide-react";
import { Link } from "react-router-dom";
import {
  getNotifications,
  markNotificationRead,
  markAllNotificationsRead,
} from "../services/notificationService";
import "../styles/BeneficiaryNotifications.css";

function BeneficiaryNotifications() {
  const [notifications, setNotifications] = useState([]);
  const [unreadCount, setUnreadCount] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [actionMessage, setActionMessage] = useState("");
  const [filter, setFilter] = useState("all"); // "all" | "unread"

  const loadData = async () => {
    setLoading(true);
    setError("");
    try {
      const response = await getNotifications();
      const items = response.data?.items || [];
      const unread = response.data?.unreadCount ?? items.filter((n) => !n.readAt).length;
      setNotifications(items);
      setUnreadCount(unread);
    } catch (err) {
      setError(
        err.response?.data?.message ||
          "Unable to load notifications. Please check your connection and try again."
      );
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadData();
  }, []);

  const handleMarkRead = async (id) => {
    try {
      await markNotificationRead(id);
      setNotifications((prev) =>
        prev.map((item) =>
          item.id === id ? { ...item, readAt: new Date().toISOString() } : item
        )
      );
      setUnreadCount((prev) => Math.max(0, prev - 1));
    } catch (err) {
      setActionMessage(
        err.response?.data?.message || "Failed to mark notification as read."
      );
    }
  };

  const handleMarkAllRead = async () => {
    try {
      await markAllNotificationsRead();
      const now = new Date().toISOString();
      setNotifications((prev) =>
        prev.map((item) => ({ ...item, readAt: item.readAt || now }))
      );
      setUnreadCount(0);
      setActionMessage("All notifications marked as read.");
      setTimeout(() => setActionMessage(""), 3000);
    } catch (err) {
      setActionMessage(
        err.response?.data?.message || "Failed to mark all as read."
      );
    }
  };

  const getNotificationIcon = (message = "") => {
    const text = message.toLowerCase();
    if (text.includes("approved") || text.includes("completed")) {
      return <Check className="notif-icon notif-icon--success" size={20} />;
    }
    if (text.includes("correction") || text.includes("rejected") || text.includes("action required")) {
      return <AlertCircle className="notif-icon notif-icon--warning" size={20} />;
    }
    if (text.includes("overdue") || text.includes("pending")) {
      return <Clock className="notif-icon notif-icon--pending" size={20} />;
    }
    return <Info className="notif-icon notif-icon--info" size={20} />;
  };

  const filteredNotifications = notifications.filter((item) => {
    if (filter === "unread") {
      return !item.readAt;
    }
    return true;
  });

  return (
    <div className="beneficiary-notifications">
      <div className="beneficiary-notifications__header">
        <div>
          <div className="beneficiary-notifications__title-row">
            <h2>Notifications</h2>
            {unreadCount > 0 && (
              <span className="beneficiary-notifications__badge">
                {unreadCount} unread
              </span>
            )}
          </div>
          <p className="beneficiary-notifications__subtitle">
            Stay informed on verification updates, officer decisions, and next steps for your grant applications.
          </p>
        </div>

        <div className="beneficiary-notifications__actions">
          <button
            className="notif-btn notif-btn--outline"
            type="button"
            onClick={loadData}
            disabled={loading}
          >
            <RefreshCw size={15} />
            Refresh
          </button>
          {unreadCount > 0 && (
            <button
              className="notif-btn notif-btn--primary"
              type="button"
              onClick={handleMarkAllRead}
            >
              <CheckCheck size={16} />
              Mark all as read
            </button>
          )}
        </div>
      </div>

      {actionMessage && (
        <div className="notif-alert notif-alert--info">{actionMessage}</div>
      )}

      {error && (
        <div className="notif-alert notif-alert--error">
          <p>{error}</p>
          <button type="button" onClick={loadData}>
            Retry
          </button>
        </div>
      )}

      <div className="beneficiary-notifications__filters">
        <button
          className={`notif-tab ${filter === "all" ? "notif-tab--active" : ""}`}
          onClick={() => setFilter("all")}
          type="button"
        >
          All ({notifications.length})
        </button>
        <button
          className={`notif-tab ${filter === "unread" ? "notif-tab--active" : ""}`}
          onClick={() => setFilter("unread")}
          type="button"
        >
          Unread ({unreadCount})
        </button>
      </div>

      {loading ? (
        <div className="beneficiary-notifications__loading">
          Loading notifications...
        </div>
      ) : filteredNotifications.length === 0 ? (
        <div className="beneficiary-notifications__empty">
          <div className="beneficiary-notifications__empty-icon">
            <Bell size={32} />
          </div>
          <h3>
            {filter === "unread"
              ? "No unread notifications"
              : "No notifications yet"}
          </h3>
          <p>
            {filter === "unread"
              ? "You are all caught up! You have no unread updates."
              : "You will receive updates here as your applications are submitted, reviewed, and approved."}
          </p>
          {filter === "all" && (
            <Link to="/beneficiary/schemes" className="notif-btn notif-btn--primary">
              Browse Available Schemes
            </Link>
          )}
        </div>
      ) : (
        <div className="beneficiary-notifications__list">
          {filteredNotifications.map((notif) => {
            const isUnread = !notif.readAt;
            const dateStr = notif.createdAt
              ? new Date(notif.createdAt).toLocaleString(undefined, {
                  month: "short",
                  day: "numeric",
                  year: "numeric",
                  hour: "2-digit",
                  minute: "2-digit",
                })
              : "Just now";

            return (
              <article
                key={notif.id}
                className={`notif-card ${isUnread ? "notif-card--unread" : ""}`}
              >
                <div className="notif-card__icon">
                  {getNotificationIcon(notif.message)}
                </div>

                <div className="notif-card__content">
                  <div className="notif-card__top">
                    <span className="notif-card__time">{dateStr}</span>
                    {isUnread && (
                      <span className="notif-card__pill">New</span>
                    )}
                  </div>

                  <p className="notif-card__message">{notif.message}</p>

                  <div className="notif-card__footer">
                    {notif.applicationId && (
                      <Link
                        to="/beneficiary/applications"
                        state={{ selectedApplicationId: notif.applicationId }}
                        className="notif-card__link"
                      >
                        View application #{notif.applicationId}
                        <ExternalLink size={14} />
                      </Link>
                    )}

                    {isUnread && (
                      <button
                        className="notif-card__read-btn"
                        type="button"
                        onClick={() => handleMarkRead(notif.id)}
                      >
                        <Check size={14} />
                        Mark as read
                      </button>
                    )}
                  </div>
                </div>
              </article>
            );
          })}
        </div>
      )}
    </div>
  );
}

export default BeneficiaryNotifications;
