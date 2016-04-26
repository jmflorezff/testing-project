#!/usr/bin/env python3

"""
Converts xml bug report data from the authors of BugLocator into JSON
"""


from roots import SRC_ROOTS
import json
import os
import re
import sys
import xml.etree.ElementTree as ET


def actual_path(file_path):
    return file_path.replace('.', os.sep, file_path.count('.') - 1)


def build_aspectj_paths(aspectj_paths):
    path_re = re.compile(r'/home/juan/Source/aspectj-1.5.3/(.*)')
    dirname = os.path.dirname(__file__)
    with open(os.path.join(dirname, 'aspectj-source-paths')) as paths_file:
        for line in paths_file:
            line = line.strip('\n')
            match = path_re.match(line)
            if not match:
                continue
            fs_path = match.group(1)
##            if fs_path in aspectj_paths:
##                print('oops:', fs_path, file=sys.stderr)
            aspectj_paths[fs_path] = line


def aspectj_check(bad_path, valid_files, aspectj_paths):
    path = re.match(r'org\.aspectj/modules/(.*)', bad_path).group(1)
    if not aspectj_paths:
        build_aspectj_paths(aspectj_paths)

##    if not path in aspectj_paths:
##        print('uhhh:', path, file=sys.stderr)
        
    real_path = aspectj_paths.get(path, '')
    if not os.path.exists(real_path):
        pass
        print('WARNING: %s does not exist in local files' % path,
              file=sys.stderr)
    else:
        valid_files.append(real_path.replace(SRC_ROOTS['AspectJ'] + os.sep,
                                             '',
                                             1))


def build_eclipse_paths(eclipse_paths):
    path_re = re.compile(r'.*/((?:org|com)/.*)')
    dirname = os.path.dirname(__file__)
    with open(os.path.join(dirname, 'eclipse-source-paths')) as paths_file:
        for line in paths_file:
            line = line.strip('\n')
            match = path_re.match(line)
            if not match:
                continue
            fs_path = actual_path(match.group(1))
##            if fs_path in eclipse_paths:
##                print('oops:', fs_path, file=sys.stderr)
            eclipse_paths[fs_path] = line


def eclipse_check(path, valid_files, eclipse_paths):
    if not eclipse_paths:
        build_eclipse_paths(eclipse_paths)

##    if not path in eclipse_paths:
##        print('uhhh:', path, file=sys.stderr)
        
    real_path = eclipse_paths.get(path, '')
    if not os.path.exists(real_path):
        print('WARNING: %s does not exist in local files' % path,
              file=sys.stderr)
    else:
        valid_files.append(real_path.replace(SRC_ROOTS['Eclipse'] + os.sep,
                                             '',
                                             1))


def main(source_file_path):
    test_file_re = re.compile('src' + os.sep + 'test' + os.sep)
    eclipse_paths = {}
    aspectj_paths = {}
    
    root = ET.parse(source_file_path).getroot()
    system = root.get('name')
    for bug in root.findall('bug'):
        info = bug.find('buginformation')
        files = bug.find('fixedFiles').findall('file')
        valid_files = []
        for file in files:
            file_path = actual_path(file.text)
            
            if test_file_re.search(file_path):
                print('WARNING: %s is a test file' % file_path,
                      file=sys.stderr)
                continue

            if not file_path.endswith('.java'):
                print('WARNING: %s is not a java file' % file_path,
                      file=sys.stderr)
                continue

            if system == 'Eclipse':
                eclipse_check(file_path, valid_files, eclipse_paths)
                continue
            elif system == 'AspectJ':
                aspectj_check(file.text, valid_files, aspectj_paths)
                continue

            if not os.path.exists(os.path.join(SRC_ROOTS[system], file_path)):
                print('WARNING: %s does not exist in local files' % file_path,
                      file=sys.stderr)
                continue

            valid_files.append(file_path)

        if not valid_files:
            print('WARNING: no valid files for bug %s' % bug.get('id'),
                  file=sys.stderr)
            continue

        print(json.dumps({'key': bug.get('id'),
                          'title': info.find('summary').text,
                          'description': info.find('description').text,
                          'creation_date': bug.get('opendate'),
                          'resolution_date': bug.get('fixdate'),
                          'fixed_files': valid_files}))
    

if __name__ == '__main__':
    if len(sys.argv) < 2:
        print('Please provide the path of the xml file to convert',
              file=sys.stderr)
        exit(1)
        
    main(sys.argv[1])
