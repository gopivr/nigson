import copy
import unittest
from verify_log_group_moves import verify, OWNERS


class LogGroupMoveTests(unittest.TestCase):
    def setUp(self):
        resources = []
        fresh = []
        for owner in OWNERS:
            address = f'module.compute.aws_cloudwatch_log_group.service["{owner}"]'
            identity = {"id": owner, "arn": "arn:synthetic:" + owner, "name": "/nigson/stage/" + owner}
            resources.append({"address": address, "previous_address": f'module.observability.aws_cloudwatch_log_group.owner["{owner}"]', "change": {"actions": ["update"], "before": copy.copy(identity), "after": copy.copy(identity)}})
            fresh.append({"type": "aws_cloudwatch_log_group", "change": {"after": {"name": identity["name"]}}})
        self.events = [{"type": "test_summary", "test_summary": {"status": "pass"}},
            {"@testrun": "create_foundation_without_images_or_running_apps", "test_plan": {"resource_changes": fresh}},
            {"@testrun": "resume_partial_apply_moves_groups_to_compute", "test_plan": {"resource_changes": resources}}]

    def test_preserved_groups_pass(self):
        self.assertIn("3 state moves", verify(self.events))

    def test_recreation_is_rejected(self):
        self.events[-1]["test_plan"]["resource_changes"][0]["change"]["actions"] = ["delete", "create"]
        with self.assertRaisesRegex(ValueError, "without creation"):
            verify(self.events)

    def test_changed_id_is_rejected(self):
        self.events[-1]["test_plan"]["resource_changes"][0]["change"]["after"]["id"] = "different"
        with self.assertRaisesRegex(ValueError, "identity"):
            verify(self.events)

    def test_duplicate_groups_are_rejected(self):
        changes = self.events[1]["test_plan"]["resource_changes"]
        changes.append(copy.deepcopy(changes[0]))
        with self.assertRaisesRegex(ValueError, "same log group"):
            verify(self.events)

    def test_failed_test_run_is_rejected(self):
        self.events[0]["test_summary"]["status"] = "fail"
        with self.assertRaisesRegex(ValueError, "did not pass"):
            verify(self.events)
