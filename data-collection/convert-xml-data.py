#!/usr/bin/env python3

"""
Converts xml bug report data from the authors of BugLocator into JSON
"""


import json
import sys
import xml.etree.ElementTree as ET


def main(source_file_path):
    root = ET.parse(source_file_path).getroot()
    for bug in root.findall('bug'):
        info = bug.find('buginformation')
        files = bug.find('fixedFiles').findall('file')

        print(json.dumps({'key': bug.get('id'),
                          'title': info.find('summary').text,
                          'description': info.find('description').text,
                          'creation_date': bug.get('opendate'),
                          'resolution_date': bug.get('fixdate'),
                          'fixed_files': [file.text for file in files]}))
    

if __name__ == '__main__':
    if len(sys.argv) < 2:
        print('Please provide the path of the xml file to convert',
              file=sys.stderr)
        exit(1)
        
    main(sys.argv[1])
