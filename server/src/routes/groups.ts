import { Router, Request, Response } from 'express';
import { v4 as uuidv4 } from 'uuid';
import { ArgusDatabase } from '../db/database';
import { Group } from '../types';

export function createGroupsRouter(db: ArgusDatabase): Router {
  const router = Router();

  /**
   * Create a new group
   */
  router.post('/create', (req: Request, res: Response): void => {
    const { userId } = (req as any).user;
    const { title, description, memberIds, avatarUrl, disappearingDurationSec } = req.body;

    if (!title || typeof title !== 'string') {
      res.status(400).json({ error: 'Group title is required' });
      return;
    }

    const members = Array.isArray(memberIds) ? Array.from(new Set([userId, ...memberIds])) : [userId];
    const group: Group = {
      id: uuidv4(),
      title: title.trim(),
      description: description ? description.trim() : undefined,
      avatarUrl,
      createdBy: userId,
      admins: [userId],
      members,
      createdAt: Date.now(),
      disappearingDurationSec
    };

    db.groups.set(group.id, group);
    db.save();

    res.json({ success: true, group });
  });

  /**
   * Get user's groups
   */
  router.get('/', (req: Request, res: Response): void => {
    const { userId } = (req as any).user;
    const userGroups: Group[] = [];

    for (const g of db.groups.values()) {
      if (g.members.includes(userId)) {
        userGroups.push(g);
      }
    }

    res.json({ groups: userGroups });
  });

  /**
   * Add members to group
   */
  router.post('/:groupId/add-members', (req: Request, res: Response): void => {
    const { userId } = (req as any).user;
    const groupId = req.params.groupId as string;
    const { memberIds } = req.body;

    const group = db.groups.get(groupId);
    if (!group) {
      res.status(404).json({ error: 'Group not found' });
      return;
    }

    if (!group.admins.includes(userId)) {
      res.status(403).json({ error: 'Only group admins can add members' });
      return;
    }

    if (Array.isArray(memberIds)) {
      memberIds.forEach((id: string) => {
        if (!group.members.includes(id)) {
          group.members.push(id);
        }
      });
      db.save();
    }

    res.json({ success: true, group });
  });

  return router;
}
