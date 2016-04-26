#!/usr/bin/env python3

"""
Downloads info for issues from Apache's JIRA API.

Input format: {"key": "<issue_key>", "fixed_files": [<list_of_fixed_files>]}
Output: JSON lines
"""


from roots import SRC_ROOTS
import http.client
import json
import os
import re
import sys
import urllib.parse


API_BATCH_SIZE = 100


def main():
    test_file_re = re.compile('src' + os.sep + 'test' + os.sep)
    local_issues = {}
    try:
        for line in sys.stdin:
            obj = json.loads(line)
            valid_files = []
            system = obj['key'].split('-')[0]
            
            for file_path in obj['fixed_files']:
                if not file_path.endswith('.java'):
                    print('WARNING: Not a java file: %s' % file_path,
                          file=sys.stderr)
                    continue

                if test_file_re.search(file_path):
                    print('WARNING: %s is a test file' % file_path,
                          file=sys.stderr)
                    continue
                    
                full_path = os.path.join(SRC_ROOTS[system], file_path)
                if not os.path.exists(full_path):
                    print('WARNING: File %s does not exist in local '
                          'source code path' % file_path, file=sys.stderr)
                    continue
                
                valid_files.append(file_path)

            if not valid_files:
                print('\nWARNING: No valid files for issue %s' % obj['key'],
                      file=sys.stderr)
                continue
                    
            local_issues[obj['key']] = valid_files
                    
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
                 'fields': 'id,key,summary,description,resolutiondate,created'
                 }))

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
                              'creation_date': issue['created'],
                              'resolution_date': issue['resolutiondate'],
                              'fixed_files': local_issues[key]}))


if __name__ == '__main__':
    main()
