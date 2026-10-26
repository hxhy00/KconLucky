"""统计服务：管理员与渠道统计。"""
from typing import List, Tuple
from ..domain import rules
from ..domain.models import AdminStats, ChannelStats
from ..infra.repositories import Repositories
class StatsService:
    """统计服务。"""
    def __init__(self, repos: Repositories):
        self.repos = repos
    def admin_stats(self) -> Tuple[AdminStats, List[dict]]:
        """管理员全局统计。"""
        totals = self.repos.stats.admin_totals()
        channels = self.repos.channel.list_all()
        quota_used = totals["approved_count"]
        stats = AdminStats(
            channel_count=totals["channel_count"],
            referrer_count=totals["referrer_count"],
            invited_count=totals["invited_count"],
            approved_count=totals["approved_count"],
            checked_in_count=totals["checked_in_count"],
            quota_total=totals["quota_total"],
            quota_used=quota_used,
            quota_remaining=totals["quota_total"] - quota_used,
            checkin_rate=rules.calc_checkin_rate(totals["checked_in_count"],
                                                 totals["approved_count"]))
        return stats, channels
    def channel_stats(self, channel_id: int) -> Tuple[ChannelStats, List[dict]]:
        """渠道统计。"""
        channel = self.repos.channel.find_by_id(channel_id)
        referrers = self.repos.referrer.list_by_channel(channel_id)
        invited = sum(r["invited_count"] for r in referrers)
        approved = sum(r["approved_count"] for r in referrers)
        checked_in = sum(r["checked_in_count"] for r in referrers)
        quota = channel["quota"] if channel else 0
        stats = ChannelStats(
            channel_name=channel["name"] if channel else "",
            quota=quota,
            referrer_cap=channel["referrer_cap"] if channel else 0,
            referrer_count=len(referrers),
            invited_count=invited,
            approved_count=approved,
            checked_in_count=checked_in,
            quota_used=approved,
            quota_remaining=quota - approved,
            checkin_rate=rules.calc_checkin_rate(checked_in, approved))
        return stats, referrers