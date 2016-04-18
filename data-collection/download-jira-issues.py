#!/usr/bin/env python3

"""
Downloads info for issues from Apache's JIRA API.

Input format: {"key": "<issue_key>", "fixed_files": [<list_of_fixed_files>]}
Output: JSON lines
"""


import http.client
import json
import sys
import urllib.parse


API_BATCH_SIZE = 100


def main():
    local_issues = {}
    try:
        for line in sys.stdin:
            obj = json.loads(line)
            local_issues[obj['key']] = obj['fixed_files']
    except (ValueError, KeyError) as _:
        print('Input format: {"key": "<issue_key>", "fixed_files": '
              '[<list_of_fixed_files>]}')
        exit(1)

    end_index = 0
    issue_ids = list(local_issues.keys())
    connection = http.client.HTTPSConnection('issues.apache.org')

    while end_index < len(issue_ids):
        start_index = end_index
        end_index = start_index + API_BATCH_SIZE

        connection.request(
            'GET', '/jira/rest/api/2/search?%s' %
            urllib.parse.urlencode(
                {'jql': 'key IN (%s)' %
                 ','.join(issue_ids[start_index:end_index]),
                 'maxResults': API_BATCH_SIZE,
                 'fields': 'id,key,summary,description,resolutiondate'}))

        response_json = json.loads(connection.getresponse().read().decode())

        if API_BATCH_SIZE > response_json['maxResults']:
            print("ERROR: API's maxResults value is lower than amount "
                  "of requests sent", file=sys.stderr)
            exit(1)

        expected_amount = min(end_index - start_index,
                              len(issue_ids) - start_index)
        actual_amount = len(response_json['issues'])

        if expected_amount != actual_amount:
            print('ERROR: API returned %d results, expected %d' %
                  (actual_amount, expected_amount), file=sys.stderr)
            exit(1)

        for wrapper in response_json['issues']:
            issue = wrapper['fields']
            key = wrapper['key']
            if issue['resolutiondate'] is None:
                print('WARNING: No resolution date for issue %s' % key,
                      file=sys.stderr)
                
            print(json.dumps({'key': key,
                              'title': issue['summary'],
                              'description': issue['description'],
                              'resolution_date': issue['resolutiondate'],
                              'fixed_files': local_issues[key]}))


if __name__ == '__main__':
    main()
